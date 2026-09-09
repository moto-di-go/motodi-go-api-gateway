package com.motodigo.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

import com.motodigo.gateway.integrations.MobileMoneyCallbackFilter;
import com.motodigo.gateway.security.GatewayJwtAuthenticationConverter;
import com.motodigo.gateway.security.RateLimitingFilter;
import com.motodigo.gateway.web.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
			AuthenticationEntryPoint authenticationEntryPoint,
			AccessDeniedHandler accessDeniedHandler,
			GatewayJwtAuthenticationConverter jwtAuthenticationConverter,
			RateLimitingFilter rateLimitingFilter,
			MobileMoneyCallbackFilter mobileMoneyCallbackFilter) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(
								"/actuator/health/**",
								"/actuator/info",
								"/error",
								"/swagger-ui/**",
								"/webjars/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/openapi", "/api/v1/docs").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/register",
								"/api/v1/auth/otp/verify",
								"/api/v1/auth/otp/resend",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/auth/password/forgot",
								"/api/v1/auth/password/reset").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/integrations/mobile-money/callback",
								"/api/v1/integrations/mobile-money/*/callback").permitAll()
						.requestMatchers("/__gateway/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/profiles").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/profiles/current").hasAuthority("PROFILE_READ")
						.requestMatchers(HttpMethod.POST, "/api/v1/profiles").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/v1/profiles/active/*").authenticated()
						.requestMatchers(HttpMethod.PATCH, "/api/v1/profiles/*").hasAuthority("PROFILE_UPDATE")
						.requestMatchers(HttpMethod.GET, "/api/v1/kyc/documents").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/kyc/documents").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/v1/drivers/position").hasAuthority("MOTOTAXI_SHARE_POSITION")
						.requestMatchers(HttpMethod.GET, "/api/v1/drivers/position").hasAuthority("MOTOTAXI_SHARE_POSITION")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/drivers/availability").hasAuthority("MOTOTAXI_SET_AVAILABILITY")
						// Public place lookup for guest browse (map + city autocomplete).
						.requestMatchers(HttpMethod.GET, "/api/v1/urban/places/popular",
								"/api/v1/urban/places/suggest",
								"/api/v1/urban/geocode",
								"/api/v1/urban/reverse").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/urban/drivers/nearby").hasAuthority("PASSENGER_SEARCH_RIDE")
						// Public road preview for guest trip-detail maps. Matching stays authenticated.
						.requestMatchers(HttpMethod.POST, "/api/v1/urban/route/preview").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/urban/route/alternatives",
								"/api/v1/urban/route/match").hasAuthority("PASSENGER_SEARCH_RIDE")
						.requestMatchers(HttpMethod.GET, "/api/v1/gis/layers").authenticated()
						.requestMatchers("/api/v1/realtime/**").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/intercity/announcements").hasAuthority("INTERCITY_ANNOUNCEMENT_WRITE")
						.requestMatchers(HttpMethod.GET, "/api/v1/intercity/announcements/mine").hasAuthority("INTERCITY_ANNOUNCEMENT_WRITE")
						// Public announcement browse for guest mode (booking stays authenticated).
						.requestMatchers(HttpMethod.GET, "/api/v1/intercity/announcements",
								"/api/v1/intercity/announcements/*").permitAll()
						.requestMatchers(HttpMethod.PATCH, "/api/v1/intercity/announcements/*").hasAuthority("INTERCITY_ANNOUNCEMENT_WRITE")
						.requestMatchers(HttpMethod.POST, "/api/v1/intercity/announcements/*/close").hasAuthority("INTERCITY_ANNOUNCEMENT_WRITE")
						.requestMatchers(HttpMethod.POST, "/api/v1/intercity/announcements/*/cancel").hasAuthority("INTERCITY_ANNOUNCEMENT_WRITE")
						.requestMatchers(HttpMethod.POST, "/api/v1/intercity/announcements/*/bookings").hasAuthority("PASSENGER_BOOK_RIDE")
						.requestMatchers(HttpMethod.GET, "/api/v1/intercity/announcements/*/bookings").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/mine", "/api/v1/bookings/*").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/confirm").hasAuthority("INTERCITY_BOOKING_CONFIRM")
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/cancel").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/pay", "/api/v1/nfc/tokens").hasAuthority("PASSENGER_PAY")
						.requestMatchers(HttpMethod.GET,
								"/api/v1/admin/kyc/pending",
								"/api/v1/admin/kyc/reviews",
								"/api/v1/admin/kyc/*/documents",
								"/api/v1/admin/kyc/documents/*/content").hasAuthority("KYC_REVIEW_READ")
						.requestMatchers(HttpMethod.POST,
								"/api/v1/admin/kyc/*/decision",
								"/api/v1/admin/kyc/documents/*/decision").hasAuthority("KYC_DECIDE")
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/moderation/queue").hasAuthority("MODERATION_READ")
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/moderation/actions").hasAuthority("MODERATION_WRITE")
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/ops/**").hasAuthority("OPERATIONS_READ")
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/pricing/rules").hasAuthority("PRICING_READ")
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/pricing/rules").hasAuthority("PRICING_WRITE")
						.requestMatchers(HttpMethod.PATCH, "/api/v1/admin/pricing/rules/*").hasAuthority("PRICING_WRITE")
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/gis/layers", "/api/v1/admin/gis/photon")
								.hasAuthority("GIS_READ")
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/gis/layers").hasAuthority("GIS_WRITE")
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/gis/photon/update").hasAuthority("GIS_WRITE")
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/gis/layers", "/api/v1/admin/gis/layers/*")
								.hasAuthority("GIS_WRITE")
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/kpi", "/api/v1/admin/kpi/export.csv").hasAuthority("KPI_READ")
						.requestMatchers("/api/v1/profiles/**",
								"/api/v1/pricing/**",
								"/api/v1/intercity/**",
								"/api/v1/ai/**",
								"/api/v1/wallet/**",
								"/api/v1/payments/**",
								"/api/v1/offline/**",
								"/api/v1/trips/**",
								"/api/v1/support/**",
								"/api/v1/notifications/**").authenticated()
						.requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN")
						.requestMatchers("/api/v1/**").authenticated()
						.anyRequest().denyAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.addFilterAfter(rateLimitingFilter, BearerTokenAuthenticationFilter.class)
				.addFilterAfter(mobileMoneyCallbackFilter, RateLimitingFilter.class)
				.headers(headers -> configureHeaders(headers, isLocalProfile(environment)));

		return http.build();
	}

	@Bean
	FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
		FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<MobileMoneyCallbackFilter> mobileMoneyCallbackFilterRegistration(
			MobileMoneyCallbackFilter filter) {
		FilterRegistrationBean<MobileMoneyCallbackFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	JwtDecoder jwtDecoder(GatewayProperties properties) {
		byte[] secret = properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8);
		SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.auth().issuerUrl());
		OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.auth().audience())
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(
						new OAuth2Error("invalid_token", "Invalid token audience", null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.cors().allowedOrigins());
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", CorrelationIdFilter.HEADER_NAME,
				"X-Signature", "Idempotency-Key", "X-Provider"));
		configuration.setExposedHeaders(Arrays.asList(CorrelationIdFilter.HEADER_NAME));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private void configureHeaders(HeadersConfigurer<HttpSecurity> headers, boolean localProfile) {
		headers
				.contentTypeOptions(Customizer.withDefaults())
				.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));

		if (localProfile) {
			headers.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
		}
		else {
			headers.httpStrictTransportSecurity(hsts -> hsts
					.includeSubDomains(true)
					.maxAgeInSeconds(31536000));
		}
	}

	private boolean isLocalProfile(Environment environment) {
		return Arrays.asList(environment.getActiveProfiles()).contains("local");
	}
}
