package com.motodigo.gateway.security;

import org.springframework.http.HttpStatus;

enum GatewayErrorCode {
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "gateway.error.auth-required"),
	AUTH_INVALID(HttpStatus.UNAUTHORIZED, "gateway.error.auth-invalid"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "gateway.error.access-denied"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "gateway.error.rate-limited");

	private final HttpStatus status;
	private final String messageKey;

	GatewayErrorCode(HttpStatus status, String messageKey) {
		this.status = status;
		this.messageKey = messageKey;
	}

	HttpStatus status() {
		return this.status;
	}

	String messageKey() {
		return this.messageKey;
	}
}
