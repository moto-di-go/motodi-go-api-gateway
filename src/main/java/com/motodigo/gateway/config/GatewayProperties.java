package com.motodigo.gateway.config;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "motodigo.gateway")
public record GatewayProperties(
		URI backendCoreBaseUrl,
		URI backendAiBaseUrl,
		Cors cors) {

	public GatewayProperties {
		if (cors == null) {
			cors = new Cors(List.of("http://localhost:3000", "http://localhost:8080"));
		}
	}

	public record Cors(List<String> allowedOrigins) {
		public Cors {
			if (allowedOrigins == null || allowedOrigins.isEmpty()) {
				allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080");
			}
		}
	}
}
