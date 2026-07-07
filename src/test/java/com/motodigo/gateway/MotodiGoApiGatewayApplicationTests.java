package com.motodigo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.motodigo.gateway.web.CorrelationIdFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.profiles.active=test")
class MotodiGoApiGatewayApplicationTests {

	private static final BlockingQueue<CapturedRequest> CAPTURED_REQUESTS = new LinkedBlockingQueue<>();
	private static HttpServer backendCoreServer;
	private static HttpServer backendAiServer;

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@DynamicPropertySource
	static void downstreamProperties(DynamicPropertyRegistry registry) {
		registry.add("motodigo.gateway.backend-core-base-url", () -> "http://localhost:" + backendCorePort());
		registry.add("motodigo.gateway.backend-ai-base-url", () -> "http://localhost:" + backendAiPort());
	}

	@BeforeEach
	void resetCapturedRequests() {
		CAPTURED_REQUESTS.clear();
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
	void contextLoads() {
	}

	@Test
	void actuatorHealthIsPublicAndIncludesCorrelationIdAndSecurityHeaders() {
		var response = this.restTemplate.getForEntity(url("/actuator/health"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
		assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
	}

	@Test
	void readinessAndLivenessArePublicOperationalEndpoints() {
		assertThat(this.restTemplate.getForEntity(url("/actuator/health/readiness"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(this.restTemplate.getForEntity(url("/actuator/health/liveness"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void versionedOpenApiAndDocsArePublicInTestProfile() {
		var openApi = this.restTemplate.getForEntity(url("/api/v1/openapi"), String.class);
		var docs = this.restTemplate.getForEntity(url("/api/v1/docs"), String.class);
		var oldOpenApiPath = this.restTemplate.getForEntity(url("/v3/api-docs/gateway"), String.class);

		assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(openApi.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
		assertThat(openApi.getBody()).contains("\"openapi\"");

		assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(docs.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
		assertThat(docs.getBody()).contains("/api/v1/openapi");

		assertThat(oldOpenApiPath.getStatusCode().is2xxSuccessful()).isFalse();
		assertThat(oldOpenApiPath.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
	}

	@Test
	void generatedCorrelationIdIsReturnedAndPropagatedToBackendCore() throws Exception {
		var response = this.restTemplate.postForEntity(url("/api/v1/auth/register"), HttpEntity.EMPTY, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String correlationId = response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME);
		assertThat(correlationId).isNotBlank();
		assertThat(UUID.fromString(correlationId)).isNotNull();

		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/auth/register");
		assertThat(request.correlationId()).isEqualTo(correlationId);
	}

	@Test
	void providedCorrelationIdIsPreservedAndPropagatedToBackendCore() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set(CorrelationIdFilter.HEADER_NAME, "corr-gate-plat-001");

		var response = this.restTemplate.exchange(
				url("/api/v1/auth/register"),
				HttpMethod.POST,
				new HttpEntity<>("", headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isEqualTo("corr-gate-plat-001");

		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.correlationId()).isEqualTo("corr-gate-plat-001");
	}

	@Test
	void aiPrefixIsRoutedToBackendAi() throws Exception {
		var response = this.restTemplate.postForEntity(url("/api/v1/ai/recommendations"), HttpEntity.EMPTY, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/ai/recommendations");
	}

	@Test
	void mobileMoneyIntegrationPrefixIsRoutedToBackendCore() throws Exception {
		var response = this.restTemplate.postForEntity(
				url("/api/v1/integrations/mobile-money/callback"),
				HttpEntity.EMPTY,
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/integrations/mobile-money/callback");
	}

	@Test
	void commonGatewayEndpointAverageLatencyStaysBelowTwoHundredMillisecondsWithoutExternalDependency() {
		for (int i = 0; i < 5; i++) {
			this.restTemplate.getForEntity(url("/api/v1/urban/drivers/nearby?lat=3.87&lon=11.52"), String.class);
		}

		var durationsMillis = new ArrayList<Double>();
		for (int i = 0; i < 20; i++) {
			long startedAt = System.nanoTime();
			var response = this.restTemplate.getForEntity(url("/api/v1/urban/drivers/nearby?lat=3.87&lon=11.52"), String.class);
			long elapsedNanos = System.nanoTime() - startedAt;

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
			durationsMillis.add(elapsedNanos / 1_000_000.0);
		}

		double averageMillis = durationsMillis.stream()
				.mapToDouble(Double::doubleValue)
				.average()
				.orElseThrow();
		assertThat(averageMillis).isLessThan(200.0);
	}

	@Test
	void unknownApiV1RouteReturnsNotFoundWithoutStacktrace() {
		var response = this.restTemplate.getForEntity(url("/api/v1/not-owned"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).doesNotContain("Exception", "stacktrace", "trace");
		assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	private static synchronized HttpServer backendCoreServer() throws IOException {
		if (backendCoreServer == null) {
			backendCoreServer = startBackendServer(201);
		}
		return backendCoreServer;
	}

	private static int backendCorePort() {
		try {
			return backendCoreServer().getAddress().getPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start backend-core test server", ex);
		}
	}

	private static synchronized HttpServer backendAiServer() throws IOException {
		if (backendAiServer == null) {
			backendAiServer = startBackendServer(202);
		}
		return backendAiServer;
	}

	private static int backendAiPort() {
		try {
			return backendAiServer().getAddress().getPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not start backend-ai test server", ex);
		}
	}

	private static HttpServer startBackendServer(int statusCode) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			CAPTURED_REQUESTS.add(new CapturedRequest(
					exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)));
			writeResponse(exchange, statusCode);
		});
		server.start();
		return server;
	}

	private static void writeResponse(HttpExchange exchange, int statusCode) throws IOException {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, body.length);
		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(body);
		}
	}

	private record CapturedRequest(String path, String correlationId) {
	}
}
