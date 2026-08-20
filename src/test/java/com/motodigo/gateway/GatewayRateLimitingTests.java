package com.motodigo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.motodigo.gateway.web.CorrelationIdFilter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.profiles.active=test")
class GatewayRateLimitingTests {

	private static final String TEST_SECRET = "test-jwt-secret-at-least-32-bytes-long";
	private static final String TEST_ISSUER = "http://localhost:8080";
	private static final String TEST_AUDIENCE = "motodi-go-api";
	private static final BlockingQueue<String> CAPTURED_PATHS = new LinkedBlockingQueue<>();
	private static HttpServer backendCoreServer;
	private static HttpServer backendAiServer;

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
			.withExposedPorts(6379);

	@LocalServerPort
	private int port;

	@Test
	void abusiveAuthRequestsAreLimitedAndExposeStableHeaders() {
		var first = this.restTemplate.postForEntity(url("/api/v1/auth/login"), HttpEntity.EMPTY, String.class);
		var second = this.restTemplate.postForEntity(url("/api/v1/auth/login"), HttpEntity.EMPTY, String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(second.getBody()).contains("\"errorCode\":\"RATE_LIMITED\"");
		assertThat(second.getHeaders().getFirst("Retry-After")).isEqualTo("120");
		assertThat(second.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("1");
		assertThat(second.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
		assertThat(second.getHeaders().getFirst("X-RateLimit-Reset")).isNotBlank();
	}

	@Test
	void callbackRequestsAreLimitedWithoutJwt() {
		String body = "{\"ref\":\"rate-limit-1\"}";
		HttpHeaders firstHeaders = signedCallbackHeaders(body, "aggregator");
		firstHeaders.set("X-Forwarded-For", "203.0.113.10");
		HttpHeaders secondHeaders = signedCallbackHeaders(body, "aggregator");
		secondHeaders.set("X-Forwarded-For", "203.0.113.10");
		var first = this.restTemplate.postForEntity(url("/api/v1/integrations/mobile-money/callback"),
				new HttpEntity<>(body, firstHeaders), String.class);
		var second = this.restTemplate.postForEntity(url("/api/v1/integrations/mobile-money/callback"),
				new HttpEntity<>(body, secondHeaders), String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(second.getBody()).contains("\"errorCode\":\"RATE_LIMITED\"");
	}

	@Test
	void providerCallbackPathIsRateLimited() {
		String body = "{\"ref\":\"rate-limit-mtn\"}";
		HttpHeaders firstHeaders = signedCallbackHeaders(body, "mtn");
		firstHeaders.set("X-Forwarded-For", "203.0.113.20");
		HttpHeaders secondHeaders = signedCallbackHeaders(body, "mtn");
		secondHeaders.set("X-Forwarded-For", "203.0.113.20");
		var first = this.restTemplate.postForEntity(url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, firstHeaders), String.class);
		var second = this.restTemplate.postForEntity(url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, secondHeaders), String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void paymentRateLimitIsSharedByJwtSubjectAcrossClientIps() throws Exception {
		HttpHeaders firstHeaders = authHeaders("subject-payment-rate-limit");
		firstHeaders.set("X-Forwarded-For", "10.0.0.1");
		HttpHeaders secondHeaders = authHeaders("subject-payment-rate-limit");
		secondHeaders.set("X-Forwarded-For", "10.0.0.2");
		HttpHeaders otherUserHeaders = authHeaders("other-subject-payment-rate-limit");
		otherUserHeaders.set("X-Forwarded-For", "10.0.0.2");

		var first = this.restTemplate.exchange(url("/api/v1/wallet/deposits"), HttpMethod.POST,
				new HttpEntity<>("{}", firstHeaders), String.class);
		var second = this.restTemplate.exchange(url("/api/v1/wallet/deposits"), HttpMethod.POST,
				new HttpEntity<>("{}", secondHeaders), String.class);
		var otherUser = this.restTemplate.exchange(url("/api/v1/wallet/deposits"), HttpMethod.POST,
				new HttpEntity<>("{}", otherUserHeaders), String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(otherUser.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(CAPTURED_PATHS.poll(2, TimeUnit.SECONDS)).isEqualTo("/api/v1/wallet/deposits");
	}

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("motodigo.gateway.backend-core-base-url", () -> "http://localhost:" + backendCorePort());
		registry.add("motodigo.gateway.backend-ai-base-url", () -> "http://localhost:" + backendAiPort());
		registry.add("motodigo.gateway.auth.jwt-secret", () -> TEST_SECRET);
		registry.add("motodigo.gateway.auth.issuer-url", () -> TEST_ISSUER);
		registry.add("motodigo.gateway.auth.audience", () -> TEST_AUDIENCE);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("motodigo.gateway.rate-limit.auth.capacity", () -> "1");
		registry.add("motodigo.gateway.rate-limit.auth.refill-tokens", () -> "1");
		registry.add("motodigo.gateway.rate-limit.auth.refill-period-seconds", () -> "120");
		registry.add("motodigo.gateway.rate-limit.otp.capacity", () -> "1");
		registry.add("motodigo.gateway.rate-limit.otp.refill-tokens", () -> "1");
		registry.add("motodigo.gateway.rate-limit.otp.refill-period-seconds", () -> "60");
		registry.add("motodigo.gateway.rate-limit.payment.capacity", () -> "1");
		registry.add("motodigo.gateway.rate-limit.payment.refill-tokens", () -> "1");
		registry.add("motodigo.gateway.rate-limit.payment.refill-period-seconds", () -> "60");
		registry.add("motodigo.gateway.rate-limit.callback.capacity", () -> "1");
		registry.add("motodigo.gateway.rate-limit.callback.refill-tokens", () -> "1");
		registry.add("motodigo.gateway.rate-limit.callback.refill-period-seconds", () -> "60");
		registry.add("motodigo.gateway.integrations.mobile-money.providers.mtn.hmac-secret", () -> "test-mtn-hmac-secret-value");
		registry.add("motodigo.gateway.integrations.mobile-money.providers.orange.hmac-secret", () -> "test-orange-hmac-secret-value");
		registry.add("motodigo.gateway.integrations.mobile-money.providers.aggregator.hmac-secret",
				() -> "test-aggregator-hmac-secret-value");
	}

	@BeforeEach
	void reset() {
		CAPTURED_PATHS.clear();
	}

	@AfterAll
	static void stopServers() {
		if (backendCoreServer != null) {
			backendCoreServer.stop(0);
		}
		if (backendAiServer != null) {
			backendAiServer.stop(0);
		}
	}

	@org.springframework.beans.factory.annotation.Autowired
	private TestRestTemplate restTemplate;

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	private static HttpHeaders signedCallbackHeaders(String body, String provider) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", "application/json");
		headers.set("Idempotency-Key", "rate-limit-" + provider + "-" + UUID.randomUUID());
		String secret = switch (provider) {
			case "mtn" -> "test-mtn-hmac-secret-value";
			case "orange" -> "test-orange-hmac-secret-value";
			default -> "test-aggregator-hmac-secret-value";
		};
		headers.set("X-Signature", hmacSha256Hex(body.getBytes(StandardCharsets.UTF_8), secret));
		return headers;
	}

	private static String hmacSha256Hex(byte[] body, String secret) {
		try {
			javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
			mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return java.util.HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static HttpHeaders authHeaders(String subject) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(jwt(subject));
		return headers;
	}

	private static String jwt(String subject) {
		try {
			Instant now = Instant.now();
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(TEST_ISSUER)
					.audience(TEST_AUDIENCE)
					.subject(subject)
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(900)))
					.claim("sid", UUID.randomUUID().toString())
					.claim("roles", List.of("PASSENGER"))
					.claim("permissions", List.of("PASSENGER_PAY"))
					.claim("activeProfileType", "PASSENGER")
					.build();
			SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			signedJWT.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
			return signedJWT.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Could not create test JWT", ex);
		}
	}

	private static int backendCorePort() {
		try {
			return backendCoreServer().getAddress().getPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start backend-core test server", ex);
		}
	}

	private static synchronized HttpServer backendCoreServer() throws IOException {
		if (backendCoreServer == null) {
			backendCoreServer = startBackendServer();
		}
		return backendCoreServer;
	}

	private static int backendAiPort() {
		try {
			return backendAiServer().getAddress().getPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start backend-ai test server", ex);
		}
	}

	private static synchronized HttpServer backendAiServer() throws IOException {
		if (backendAiServer == null) {
			backendAiServer = startBackendServer();
		}
		return backendAiServer;
	}

	private static HttpServer startBackendServer() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", GatewayRateLimitingTests::writeResponse);
		server.start();
		return server;
	}

	private static void writeResponse(HttpExchange exchange) throws IOException {
		CAPTURED_PATHS.add(exchange.getRequestURI().getPath());
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.getResponseHeaders().set(CorrelationIdFilter.HEADER_NAME,
				exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER_NAME));
		exchange.sendResponseHeaders(201, body.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(body);
		}
	}
}
