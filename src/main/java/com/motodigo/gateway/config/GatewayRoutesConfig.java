package com.motodigo.gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
class GatewayRoutesConfig {

	private static final String[] BACKEND_CORE_PREFIXES = {
			"/api/v1/auth/**",
			"/api/v1/profiles",
			"/api/v1/profiles/**",
			"/api/v1/kyc/**",
			"/api/v1/vehicles/**",
			"/api/v1/drivers/**",
			"/api/v1/urban/**",
			"/api/v1/pricing/**",
			"/api/v1/gis/**",
			"/api/v1/intercity/**",
			"/api/v1/bookings/**",
			"/api/v1/wallet/**",
			"/api/v1/payments/**",
			"/api/v1/receipts/**",
			"/api/v1/nfc/**",
			"/api/v1/trips/**",
			"/api/v1/offline/**",
			"/api/v1/notifications/**",
			"/api/v1/support/**",
			"/api/v1/integrations/mobile-money/**",
			"/api/v1/agency/**",
			"/api/v1/admin/**"
	};

	private static final String[] BACKEND_CORE_REALTIME_PREFIXES = {
			"/api/v1/realtime/**"
	};

	@Bean
	RouterFunction<ServerResponse> backendCoreRoutes(GatewayProperties properties) {
		var builder = route("backend-core-api-v1");
		for (String prefix : BACKEND_CORE_PREFIXES) {
			builder.route(path(prefix), http());
		}
		return builder
				.filter(circuitBreaker("backendCore"))
				.before(uri(properties.backendCoreBaseUrl()))
				.build();
	}

	/**
	 * Realtime (SSE / WS handshake paths) uses a dedicated circuit breaker with a long timeout
	 * so Resilience4j does not cut Server-Sent Events after the short core API timeout.
	 */
	@Bean
	RouterFunction<ServerResponse> backendCoreRealtimeRoutes(GatewayProperties properties) {
		var builder = route("backend-core-realtime-api-v1");
		for (String prefix : BACKEND_CORE_REALTIME_PREFIXES) {
			builder.route(path(prefix), http());
		}
		return builder
				.filter(circuitBreaker("backendCoreRealtime"))
				.before(uri(properties.backendCoreBaseUrl()))
				.build();
	}

	@Bean
	RouterFunction<ServerResponse> backendAiRoutes(GatewayProperties properties) {
		return route("backend-ai-api-v1")
				.route(path("/api/v1/ai/**"), http())
				.filter(circuitBreaker(config -> config
						.setId("backendAi")
						.setFallbackUri(URI.create("forward:/__gateway/ai/fallback"))
						.setStatusCodes("500", "502", "503", "504")))
				.before(uri(properties.backendAiBaseUrl()))
				.build();
	}
}
