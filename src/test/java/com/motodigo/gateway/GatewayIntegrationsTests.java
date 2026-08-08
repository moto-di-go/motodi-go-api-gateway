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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.motodigo.gateway.web.CorrelationIdFilter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
class GatewayIntegrationsTests {

	private static final String TEST_SECRET = "test-jwt-secret-at-least-32-bytes-long";
	private static final String TEST_ISSUER = "http://localhost:8080";
	private static final String TEST_AUDIENCE = "motodi-go-api";
	private static final String MTN_HMAC = "test-mtn-hmac-secret-value";
	private static final String ORANGE_HMAC = "test-orange-hmac-secret-value";
	private static final String AGGREGATOR_HMAC = "test-aggregator-hmac-secret-value";

	private static final BlockingQueue<CapturedRequest> CORE_CAPTURES = new LinkedBlockingQueue<>();
	private static final AtomicInteger CORE_HITS = new AtomicInteger();
	private static final AtomicInteger AI_MODE = new AtomicInteger(0); // 0=ok, 1=500, 2=slow

	private static HttpServer backendCoreServer;
	private static HttpServer backendAiServer;

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
			.withExposedPorts(6379);

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@DynamicPropertySource
	static void dynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("motodigo.gateway.backend-core-base-url", () -> "http://localhost:" + backendCorePort());
		registry.add("motodigo.gateway.backend-ai-base-url", () -> "http://localhost:" + backendAiPort());
		registry.add("motodigo.gateway.auth.jwt-secret", () -> TEST_SECRET);
		registry.add("motodigo.gateway.auth.issuer-url", () -> TEST_ISSUER);
		registry.add("motodigo.gateway.auth.audience", () -> TEST_AUDIENCE);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("motodigo.gateway.rate-limit.enabled", () -> "false");
		registry.add("motodigo.gateway.integrations.mobile-money.providers.mtn.hmac-secret", () -> MTN_HMAC);
		registry.add("motodigo.gateway.integrations.mobile-money.providers.orange.hmac-secret", () -> ORANGE_HMAC);
		registry.add("motodigo.gateway.integrations.mobile-money.providers.aggregator.hmac-secret", () -> AGGREGATOR_HMAC);
		registry.add("motodigo.gateway.integrations.mobile-money.providers.mtn.allowed-source-ips[0]", () -> "127.0.0.1");
		registry.add("motodigo.gateway.integrations.mobile-money.providers.mtn.allowed-source-ips[1]", () -> "::1");
		registry.add("motodigo.gateway.integrations.mobile-money.providers.mtn.allowed-source-ips[2]", () -> "0:0:0:0:0:0:0:1");
		registry.add("resilience4j.timelimiter.instances.backendAi.timeoutDuration", () -> "1s");
	}

	@BeforeEach
	void reset() {
		CORE_CAPTURES.clear();
		CORE_HITS.set(0);
		AI_MODE.set(0);
		this.circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
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

	@Test
	void unsignedCallbackIsRejectedWithoutForwarding() throws Exception {
		String body = "{\"externalReference\":\"unsigned-1\"}";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Idempotency-Key", "unsigned-1");

		var response = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"errorCode\":\"CALLBACK_SIGNATURE_INVALID\"");
		assertThat(response.getBody()).doesNotContain(MTN_HMAC).doesNotContain(body);
		assertThat(CORE_CAPTURES.poll(500, TimeUnit.MILLISECONDS)).isNull();
		assertThat(CORE_HITS.get()).isZero();
	}

	@Test
	void invalidSignatureIsRejectedWithoutForwarding() {
		String body = "{\"externalReference\":\"bad-sig\"}";
		HttpHeaders headers = signedHeaders(body, MTN_HMAC, "bad-sig");
		headers.set("X-Signature", "deadbeef");

		var response = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("CALLBACK_SIGNATURE_INVALID");
		assertThat(CORE_HITS.get()).isZero();
	}

	@Test
	void disallowedOriginIsRejectedWithoutForwarding() {
		String body = "{\"externalReference\":\"bad-origin\"}";
		HttpHeaders headers = signedHeaders(body, MTN_HMAC, "bad-origin");
		headers.set("X-Forwarded-For", "203.0.113.10");

		var response = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("CALLBACK_ORIGIN_DENIED");
		assertThat(CORE_HITS.get()).isZero();
	}

	@Test
	void validCallbackIsForwardedOnceAndReplayIsRejected() throws Exception {
		String body = "{\"externalReference\":\"once-1\",\"status\":\"SUCCESS\"}";
		HttpHeaders headers = signedHeaders(body, MTN_HMAC, "once-1");

		var first = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, headers),
				String.class);
		var second = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/mtn/callback"),
				new HttpEntity<>(body, headers),
				String.class);

		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(second.getBody()).contains("CALLBACK_REPLAY");
		assertThat(CORE_HITS.get()).isEqualTo(1);
		CapturedRequest captured = CORE_CAPTURES.poll(2, TimeUnit.SECONDS);
		assertThat(captured).isNotNull();
		assertThat(captured.path()).isEqualTo("/api/v1/integrations/mobile-money/mtn/callback");
		assertThat(CORE_CAPTURES.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void orangeAndAggregatorProviderRoutesAreForwarded() throws Exception {
		String orangeBody = "{\"externalReference\":\"orange-1\"}";
		var orange = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/orange/callback"),
				new HttpEntity<>(orangeBody, signedHeaders(orangeBody, ORANGE_HMAC, "orange-1")),
				String.class);
		String aggBody = "{\"externalReference\":\"agg-1\"}";
		var aggregator = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/aggregator/callback"),
				new HttpEntity<>(aggBody, signedHeaders(aggBody, AGGREGATOR_HMAC, "agg-1")),
				String.class);

		assertThat(orange.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(aggregator.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(CORE_HITS.get()).isEqualTo(2);
	}

	@Test
	void aiRecommendationsAreProxiedWhenAiIsHealthy() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/ai/recommendations"),
				HttpMethod.POST,
				new HttpEntity<>("{}", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody()).doesNotContain("fallback\":true");
	}

	@Test
	void aiUnavailableReturnsExploitableFallbackWithoutSensitiveLeak() {
		AI_MODE.set(1);

		var response = this.restTemplate.exchange(
				url("/api/v1/ai/recommendations"),
				HttpMethod.POST,
				new HttpEntity<>("{}", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
				.contains("\"fallback\":true")
				.contains("\"recommendations\":[]")
				.contains("\"message\":")
				.doesNotContain("Exception")
				.doesNotContain("stacktrace")
				.doesNotContain(TEST_SECRET);
	}

	@Test
	void aiTimeoutReturnsExploitableFallback() {
		AI_MODE.set(2);

		var response = this.restTemplate.exchange(
				url("/api/v1/ai/recommendations"),
				HttpMethod.POST,
				new HttpEntity<>("{}", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"fallback\":true");
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	private static HttpHeaders signedHeaders(String body, String secret, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Idempotency-Key", idempotencyKey);
		headers.set("X-Signature", hmacSha256Hex(body.getBytes(StandardCharsets.UTF_8), secret));
		headers.set("X-Forwarded-For", "127.0.0.1");
		return headers;
	}

	private static HttpHeaders authHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(jwt());
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static String jwt() {
		try {
			Instant now = Instant.now();
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(TEST_ISSUER)
					.audience(TEST_AUDIENCE)
					.subject(UUID.randomUUID().toString())
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(900)))
					.claim("sid", UUID.randomUUID().toString())
					.claim("roles", List.of("PASSENGER"))
					.claim("permissions", List.of())
					.claim("activeProfileType", "PASSENGER")
					.build();
			SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			signedJWT.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
			return signedJWT.serialize();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String hmacSha256Hex(byte[] body, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return java.util.HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static synchronized HttpServer backendCoreServer() throws IOException {
		if (backendCoreServer == null) {
			backendCoreServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			backendCoreServer.setExecutor(Executors.newCachedThreadPool());
			backendCoreServer.createContext("/", exchange -> {
				CORE_HITS.incrementAndGet();
				CORE_CAPTURES.add(new CapturedRequest(exchange.getRequestURI().getPath()));
				writeJson(exchange, 201, "{}");
			});
			backendCoreServer.start();
		}
		return backendCoreServer;
	}

	private static int backendCorePort() {
		try {
			return backendCoreServer().getAddress().getPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static synchronized HttpServer backendAiServer() throws IOException {
		if (backendAiServer == null) {
			backendAiServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			backendAiServer.setExecutor(Executors.newCachedThreadPool());
			backendAiServer.createContext("/", exchange -> {
				int mode = AI_MODE.get();
				if (mode == 1) {
					writeJson(exchange, 500, "{\"error\":\"boom\"}");
					return;
				}
				if (mode == 2) {
					try {
						Thread.sleep(2500);
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				writeJson(exchange, 202, "{\"recommendations\":[{\"announcement_id\":\"a1\"}],\"fallback\":false,\"message\":\"ok\"}");
			});
			backendAiServer.start();
		}
		return backendAiServer;
	}

	private static int backendAiPort() {
		try {
			return backendAiServer().getAddress().getPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void writeJson(HttpExchange exchange, int status, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.getResponseHeaders().set(CorrelationIdFilter.HEADER_NAME,
				exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER_NAME));
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(body);
		}
	}

	private record CapturedRequest(String path) {
	}
}
