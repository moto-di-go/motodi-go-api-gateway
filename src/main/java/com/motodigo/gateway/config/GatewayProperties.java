package com.motodigo.gateway.config;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
		Cors cors) {

	public GatewayProperties {
		if (cors == null) {
			cors = new Cors(List.of("http://localhost:3000", "http://localhost:8080"));
		}
	}

	public record Cors(@NotEmpty List<String> allowedOrigins) {
		public Cors {
			if (allowedOrigins == null || allowedOrigins.isEmpty()) {
				allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");
			}
		}
	}
}
