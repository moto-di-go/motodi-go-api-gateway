package com.motodigo.gateway.config;

import java.util.Arrays;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class ExternalGatewayConfigurationValidator implements SmartInitializingSingleton {

	private static final Set<String> LOCAL_PROFILES = Set.of("local", "test");
	private static final String DEFAULT_JWT_SECRET = "change-me-to-at-least-32-bytes-for-hs256";

	private final Environment environment;
	private final GatewayProperties properties;

	ExternalGatewayConfigurationValidator(Environment environment, GatewayProperties properties) {
		this.environment = environment;
		this.properties = properties;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (isLocalOrTest()) {
			return;
		}
		for (String origin : this.properties.cors().allowedOrigins()) {
			if (isLocalOrigin(origin)) {
				throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not include local origins outside local/test profiles");
			}
		}
		requireNonDefault("JWT_SECRET", this.properties.auth().jwtSecret(), DEFAULT_JWT_SECRET);
	}

	private boolean isLocalOrTest() {
		return Arrays.stream(this.environment.getActiveProfiles()).anyMatch(LOCAL_PROFILES::contains);
	}

	private static boolean isLocalOrigin(String origin) {
		return origin.contains("localhost") || origin.contains("127.0.0.1") || origin.contains("[::1]");
	}

	private static void requireNonDefault(String name, String actualValue, String defaultValue) {
		if (defaultValue.equals(actualValue)) {
			throw new IllegalStateException(name + " must be configured outside local/test profiles");
		}
	}
}
