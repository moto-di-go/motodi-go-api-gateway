package com.motodigo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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

	private static final String TEST_SECRET = "test-jwt-secret-at-least-32-bytes-long";
	private static final String TEST_ISSUER = "http://localhost:8080";
	private static final String TEST_AUDIENCE = "motodi-go-api";
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
		registry.add("motodigo.gateway.auth.jwt-secret", () -> TEST_SECRET);
		registry.add("motodigo.gateway.auth.issuer-url", () -> TEST_ISSUER);
		registry.add("motodigo.gateway.auth.audience", () -> TEST_AUDIENCE);
		registry.add("motodigo.gateway.rate-limit.enabled", () -> "false");
		registry.add("motodigo.gateway.integrations.mobile-money.enabled", () -> "false");
		registry.add("management.health.redis.enabled", () -> "false");
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
		var swaggerCss = this.restTemplate.getForEntity(url("/webjars/swagger-ui/5.32.0/swagger-ui.css"), String.class);
		var oldOpenApiPath = this.restTemplate.getForEntity(url("/v3/api-docs/gateway"), String.class);

		assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(openApi.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
		assertThat(openApi.getBody())
				.contains("openapi: 3.0.3")
				.contains("/api/v1/auth/register")
				.contains("/api/v1/auth/otp/verify")
				.contains("/api/v1/auth/otp/resend")
				.contains("/api/v1/auth/login")
				.contains("/api/v1/auth/refresh")
				.contains("/api/v1/auth/logout")
				.contains("/api/v1/auth/password/forgot")
				.contains("/api/v1/auth/password/reset");
		assertThat(openApi.getBody()).contains(requiredContractPaths().toArray(String[]::new));
		assertThat(openApi.getBody())
				.contains("errorCode:")
				.contains("timestamp:")
				.contains("otpExpiresAt:")
				.contains("OtpVerifyResponse:")
				.contains("OtpResendResponse:")
				.contains("accessTokenExpiresAt:")
				.contains("refreshTokenExpiresAt:")
				.contains("userId:")
				.contains("phoneNumber:")
				.contains("CreateProfileRequest:")
				.contains("UpdateProfileRequest:")
				.contains("ProfileResponse:")
				.contains("ProfileTokenSummary:")
				.contains("activeProfileId:")
				.contains("permissions:")
				.contains("required: [code, newPassword]");
		assertThat(openApi.getBody())
				.doesNotContain("otpCode:")
				.doesNotContain("expiresIn:")
				.doesNotContain("required: [purpose, code]")
				.doesNotContain("required: [otpCode, newPassword]");

		assertThat(docs.getStatusCode().is2xxSuccessful() || docs.getStatusCode().is3xxRedirection()).isTrue();
		assertThat(docs.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
		if (docs.getStatusCode().is2xxSuccessful()) {
			assertThat(docs.getBody()).contains("Swagger UI", "/api/v1/openapi", "/webjars/swagger-ui/5.32.0/swagger-ui.css");
		}
		else {
			assertThat(docs.getHeaders().getLocation().toString()).contains("/swagger-ui");
		}

		assertThat(swaggerCss.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(swaggerCss.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
		assertThat(swaggerCss.getBody()).contains(".swagger-ui");

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
	void privateEndpointWithoutJwtReturnsStableUnauthorizedError() {
		var response = this.restTemplate.getForEntity(url("/api/v1/profiles/current"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody())
				.contains("\"errorCode\":\"AUTH_REQUIRED\"")
				.contains("\"correlationId\":")
				.doesNotContain("Exception", "stacktrace", TEST_SECRET);
		assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
	}

	@Test
	void invalidJwtReturnsStableUnauthorizedError() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("not-a-jwt");

		var response = this.restTemplate.exchange(
				url("/api/v1/profiles/current"),
				HttpMethod.GET,
				new HttpEntity<>("", headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody())
				.contains("\"errorCode\":\"AUTH_INVALID\"")
				.doesNotContain("not-a-jwt", TEST_SECRET, "BadJwtException");
	}

	@Test
	void wrongAudienceJwtReturnsStableUnauthorizedError() throws Exception {
		HttpHeaders headers = authHeaders(List.of("PROFILE_READ"), List.of("motodi-go-other-api"));

		var response = this.restTemplate.exchange(
				url("/api/v1/profiles/current"),
				HttpMethod.GET,
				new HttpEntity<>("", headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"errorCode\":\"AUTH_INVALID\"");
	}

	@Test
	void missingPermissionReturnsStableForbiddenError() throws Exception {
		HttpHeaders headers = authHeaders(List.of("PROFILE_READ"));

		var response = this.restTemplate.exchange(
				url("/api/v1/drivers/position"),
				HttpMethod.PUT,
				new HttpEntity<>("{}", headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody())
				.contains("\"errorCode\":\"ACCESS_DENIED\"")
				.doesNotContain(TEST_SECRET, "AccessDeniedException");
	}

	@Test
	void permittedJwtRoutesRequestAndPropagatesAuthorization() throws Exception {
		HttpHeaders headers = authHeaders(List.of("MOTOTAXI_SHARE_POSITION"));

		var response = this.restTemplate.exchange(
				url("/api/v1/drivers/position"),
				HttpMethod.PUT,
				new HttpEntity<>("{}", headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/drivers/position");
		assertThat(request.authorization()).startsWith("Bearer ");
		assertThat(request.correlationId()).isNotBlank();
	}

	@Test
	void realtimeSseRouteExistsAndRequiresJwt() {
		var unauthorized = this.restTemplate.getForEntity(url("/api/v1/realtime/sse"), String.class);
		assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unauthorized.getBody()).contains("\"errorCode\":\"AUTH_REQUIRED\"");
	}

	@Test
	void realtimeSseIsRoutedToBackendCoreWithJwt() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/realtime/sse?channel=trip"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/realtime/sse");
		assertThat(request.authorization()).startsWith("Bearer ");
		assertThat(request.correlationId()).isNotBlank();
	}

	@Test
	void realtimeChannelsIsRoutedToBackendCoreWithJwt() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/realtime/channels"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/realtime/channels");
	}

	@Test
	void kycUploadIsRoutedWithAuthenticatedJwtEvenBeforeRefreshedProfilePermissions() throws Exception {
		HttpHeaders headers = authHeaders(List.of("PROFILE_READ"));

		var response = this.restTemplate.exchange(
				url("/api/v1/kyc/documents"),
				HttpMethod.POST,
				new HttpEntity<>("", headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/kyc/documents");
		assertThat(request.authorization()).startsWith("Bearer ");
	}

	@Test
	void profileListIsRoutedWithAuthenticatedJwtEvenBeforeProfileReadPermission() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/profiles"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/profiles");
		assertThat(request.authorization()).startsWith("Bearer ");
	}

	@Test
	void walletSummaryIsRoutedToBackendCoreForAuthenticatedUser() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/wallet"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/wallet");
		assertThat(request.authorization()).startsWith("Bearer ");
		assertThat(request.correlationId()).isNotBlank();
	}

	@Test
	void walletLedgerIsRoutedToBackendCoreWithQueryPreserved() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/wallet/ledger?page=0&size=20"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/wallet/ledger");
		assertThat(request.query()).isEqualTo("page=0&size=20");
		assertThat(request.authorization()).startsWith("Bearer ");
	}

	@Test
	void walletRequiresAuthenticationAtGateway() {
		var response = this.restTemplate.getForEntity(url("/api/v1/wallet"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"errorCode\":\"AUTH_REQUIRED\"");
	}

	@Test
	void profileActivationIsRoutedWithAuthenticatedJwtEvenBeforeProfileSwitchPermission() throws Exception {
		UUID profileId = UUID.randomUUID();

		var response = this.restTemplate.exchange(
				url("/api/v1/profiles/active/" + profileId),
				HttpMethod.PATCH,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/profiles/active/" + profileId);
		assertThat(request.authorization()).startsWith("Bearer ");
	}

	@Test
	void adminKycDocumentContentRequiresReviewPermission() throws Exception {
		UUID documentId = UUID.randomUUID();

		var forbidden = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/documents/" + documentId + "/content"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders(List.of("PROFILE_READ"))),
				String.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		var allowed = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/documents/" + documentId + "/content"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders(List.of("KYC_REVIEW_READ"))),
				String.class);

		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/admin/kyc/documents/" + documentId + "/content");
	}

	@Test
	void adminKycPendingRequiresReviewPermission() throws Exception {
		var forbidden = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/pending"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders(List.of("PROFILE_READ"))),
				String.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		var allowed = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/pending"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders(List.of("KYC_REVIEW_READ"))),
				String.class);

		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/admin/kyc/pending");
	}

	@Test
	void adminKycReviewsRequiresReviewPermission() throws Exception {
		var forbidden = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/reviews?profileType=MOTOTAXI&status=PENDING"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders(List.of("PROFILE_READ"))),
				String.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		var allowed = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/reviews?profileType=MOTOTAXI&status=PENDING"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders(List.of("KYC_REVIEW_READ"))),
				String.class);

		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/admin/kyc/reviews");
		assertThat(request.query()).isEqualTo("profileType=MOTOTAXI&status=PENDING");
	}

	@Test
	void adminKycDecisionRequiresDecisionPermission() throws Exception {
		UUID profileId = UUID.randomUUID();

		var forbidden = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/" + profileId + "/decision"),
				HttpMethod.POST,
				new HttpEntity<>("{\"decision\":\"VALIDATE\"}", authHeaders(List.of("KYC_REVIEW_READ"))),
				String.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		var allowed = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/" + profileId + "/decision"),
				HttpMethod.POST,
				new HttpEntity<>("{\"decision\":\"VALIDATE\"}", authHeaders(List.of("KYC_DECIDE"))),
				String.class);

		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/admin/kyc/" + profileId + "/decision");
	}

	@Test
	void adminKycDocumentDecisionRequiresDecisionPermission() throws Exception {
		UUID documentId = UUID.randomUUID();

		var forbidden = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/documents/" + documentId + "/decision"),
				HttpMethod.POST,
				new HttpEntity<>("{\"decision\":\"REJECT\",\"reason\":\"Photo illisible\"}",
						authHeaders(List.of("KYC_REVIEW_READ"))),
				String.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		var allowed = this.restTemplate.exchange(
				url("/api/v1/admin/kyc/documents/" + documentId + "/decision"),
				HttpMethod.POST,
				new HttpEntity<>("{\"decision\":\"REJECT\",\"reason\":\"Photo illisible\"}",
						authHeaders(List.of("KYC_DECIDE"))),
				String.class);

		assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/admin/kyc/documents/" + documentId + "/decision");
	}

	@Test
	void aiPrefixIsRoutedToBackendAi() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/ai/recommendations"),
				HttpMethod.POST,
				new HttpEntity<>("", authHeaders()),
				String.class);

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
	void exactProfilesCollectionRouteIsRoutedToBackendCore() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/profiles"),
				HttpMethod.POST,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		CapturedRequest request = CAPTURED_REQUESTS.poll(2, TimeUnit.SECONDS);
		assertThat(request).isNotNull();
		assertThat(request.path()).isEqualTo("/api/v1/profiles");
	}

	@Test
	void commonGatewayEndpointAverageLatencyStaysBelowTwoHundredMillisecondsWithoutExternalDependency() {
		for (int i = 0; i < 5; i++) {
			this.restTemplate.exchange(url("/api/v1/urban/drivers/nearby?lat=3.87&lon=11.52"), HttpMethod.GET,
					new HttpEntity<>("", authHeaders(List.of("PASSENGER_SEARCH_RIDE"))), String.class);
		}

		var durationsMillis = new ArrayList<Double>();
		for (int i = 0; i < 20; i++) {
			long startedAt = System.nanoTime();
			var response = this.restTemplate.exchange(url("/api/v1/urban/drivers/nearby?lat=3.87&lon=11.52"),
					HttpMethod.GET,
					new HttpEntity<>("", authHeaders(List.of("PASSENGER_SEARCH_RIDE"))),
					String.class);
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
	void unknownApiV1RouteReturnsNotFoundWithoutStacktrace() throws Exception {
		var response = this.restTemplate.exchange(
				url("/api/v1/not-owned"),
				HttpMethod.GET,
				new HttpEntity<>("", authHeaders()),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).doesNotContain("Exception", "stacktrace", "trace");
		assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
	}

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	private static HttpHeaders authHeaders(String... permissions) {
		return authHeaders(List.of(permissions));
	}

	private static HttpHeaders authHeaders(List<String> permissions) {
		return authHeaders(permissions, List.of(TEST_AUDIENCE));
	}

	private static HttpHeaders authHeaders(List<String> permissions, List<String> audience) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(jwt(permissions, audience));
		return headers;
	}

	private static String jwt(List<String> permissions, List<String> audience) {
		try {
			Instant now = Instant.now();
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.issuer(TEST_ISSUER)
					.audience(audience)
					.subject(UUID.randomUUID().toString())
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(900)))
					.claim("sid", UUID.randomUUID().toString())
					.claim("roles", List.of("PASSENGER"))
					.claim("permissions", permissions)
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

	private static List<String> requiredContractPaths() {
		return List.of(
				"/api/v1/profiles",
				"/api/v1/profiles/current",
				"/api/v1/profiles/{profileId}",
				"/api/v1/profiles/active/{profileId}",
				"/api/v1/kyc/documents",
				"/api/v1/vehicles/mine",
				"/api/v1/vehicles/{id}/photo",
				"/api/v1/admin/kyc/pending",
				"/api/v1/admin/kyc/{profileId}/documents",
				"/api/v1/admin/kyc/{profileId}/decision",
				"/api/v1/drivers/position",
				"/api/v1/urban/drivers/nearby",
				"/api/v1/urban/places/popular",
				"/api/v1/urban/places/suggest",
				"/api/v1/urban/geocode",
				"/api/v1/urban/reverse",
				"/api/v1/urban/route/preview",
				"/api/v1/urban/route/alternatives",
				"/api/v1/urban/route/match",
				"/api/v1/pricing/estimate",
				"/api/v1/intercity/announcements",
				"/api/v1/intercity/announcements/mine",
				"/api/v1/intercity/announcements/{id}",
				"/api/v1/intercity/announcements/{id}/close",
				"/api/v1/intercity/announcements/{id}/cancel",
				"/api/v1/intercity/announcements/{id}/bookings",
				"/api/v1/bookings/mine",
				"/api/v1/bookings/{id}",
				"/api/v1/bookings/{id}/confirm",
				"/api/v1/bookings/{id}/cancel",
				"/api/v1/ai/recommendations",
				"/api/v1/wallet",
				"/api/v1/wallet/ledger",
				"/api/v1/wallet/deposits",
				"/api/v1/integrations/mobile-money/callback",
				"/api/v1/integrations/mobile-money/mtn/callback",
				"/api/v1/integrations/mobile-money/orange/callback",
				"/api/v1/integrations/mobile-money/aggregator/callback",
				"/api/v1/bookings/{id}/pay",
				"/api/v1/nfc/eligibility",
				"/api/v1/nfc/tokens",
				"/api/v1/nfc/tokens/{id}",
				"/api/v1/offline/sync",
				"/api/v1/offline/operations",
				"/api/v1/offline/map-areas",
				"/api/v1/trips/active",
				"/api/v1/trips/history",
				"/api/v1/trips/{id}/status",
				"/api/v1/trips/{id}/complete",
				"/api/v1/trips/{id}/ratings",
				"/api/v1/trips/{id}/events",
				"/api/v1/receipts",
				"/api/v1/receipts/{id}",
				"/api/v1/support/tickets",
				"/api/v1/support/tickets/{id}",
				"/api/v1/admin/moderation/queue",
				"/api/v1/admin/moderation/actions",
				"/api/v1/admin/support/tickets",
				"/api/v1/admin/support/tickets/{id}",
				"/api/v1/admin/ops/overview",
				"/api/v1/admin/ops/users",
				"/api/v1/admin/ops/users/{id}",
				"/api/v1/admin/ops/announcements",
				"/api/v1/admin/ops/bookings",
				"/api/v1/admin/ops/payments",
				"/api/v1/admin/pricing/rules",
				"/api/v1/admin/gis/layers",
				"/api/v1/admin/gis/photon",
				"/api/v1/admin/gis/photon/update",
				"/api/v1/gis/layers",
				"/api/v1/realtime/sse",
				"/api/v1/realtime/channels",
				"/api/v1/realtime/ws",
				"/api/v1/drivers/availability",
				"/api/v1/admin/kpi",
				"/api/v1/admin/kpi/export.csv",
				"/api/v1/agency/overview",
				"/api/v1/agency/announcements",
				"/api/v1/agency/bookings",
				"/actuator/health",
				"/actuator/health/readiness",
				"/actuator/health/liveness");
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
					exchange.getRequestURI().getQuery(),
					exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER_NAME),
					exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
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

	private record CapturedRequest(String path, String query, String correlationId, String authorization) {
	}
}
