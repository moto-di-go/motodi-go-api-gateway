package com.motodigo.gateway.config;

import java.util.Arrays;

import com.motodigo.gateway.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
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
						.requestMatchers("/api/v1/**").permitAll()
						.anyRequest().denyAll())
				.headers(headers -> configureHeaders(headers, isLocalProfile(environment)));

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.cors().allowedOrigins());
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", CorrelationIdFilter.HEADER_NAME));
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
