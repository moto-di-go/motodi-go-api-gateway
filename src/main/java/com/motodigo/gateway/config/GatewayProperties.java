package com.motodigo.gateway.config;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "motodigo.gateway")
public record GatewayProperties(
		@NotNull
		URI backendCoreBaseUrl,
		@NotNull
		URI backendAiBaseUrl,
		@Valid @NotNull
		Cors cors,
		@Valid @NotNull
		Auth auth,
		@Valid @NotNull
		RateLimit rateLimit) {

	public GatewayProperties {
		if (cors == null) {
			cors = new Cors(List.of("http://localhost:3000", "http://localhost:8080"));
		}
		if (auth == null) {
			auth = new Auth("change-me-to-at-least-32-bytes-for-hs256", "http://localhost:8080", "motodi-go-api");
		}
		if (rateLimit == null) {
			rateLimit = RateLimit.defaults();
		}
	}

	public record Cors(@NotEmpty List<String> allowedOrigins) {
		public Cors {
			if (allowedOrigins == null || allowedOrigins.isEmpty()) {
				allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");
			}
		}
	}

	public record Auth(
			@NotBlank @Size(min = 32)
			String jwtSecret,
			@NotBlank
			String issuerUrl,
			@NotBlank
			String audience) {
		public Auth {
			if (jwtSecret == null || jwtSecret.isBlank()) {
				jwtSecret = "change-me-to-at-least-32-bytes-for-hs256";
			}
			if (issuerUrl == null || issuerUrl.isBlank()) {
				issuerUrl = "http://localhost:8080";
			}
			if (audience == null || audience.isBlank()) {
				audience = "motodi-go-api";
			}
		}
	}

	public record RateLimit(
			boolean enabled,
			@Valid @NotNull
			Bucket auth,
			@Valid @NotNull
			Bucket otp,
			@Valid @NotNull
			Bucket payment,
			@Valid @NotNull
			Bucket callback) {
		public RateLimit {
			if (auth == null) {
				auth = new Bucket(5, 1, 120);
			}
			if (otp == null) {
				otp = new Bucket(5, 1, 120);
			}
			if (payment == null) {
				payment = new Bucket(10, 10, 60);
			}
			if (callback == null) {
				callback = new Bucket(60, 60, 60);
			}
		}

		static RateLimit defaults() {
			return new RateLimit(true, new Bucket(5, 1, 120), new Bucket(5, 1, 120), new Bucket(10, 10, 60),
					new Bucket(60, 60, 60));
		}
	}

	public record Bucket(
			@Min(1)
			long capacity,
			@Min(1)
			long refillTokens,
			@Min(1)
			long refillPeriodSeconds) {
	}
}
