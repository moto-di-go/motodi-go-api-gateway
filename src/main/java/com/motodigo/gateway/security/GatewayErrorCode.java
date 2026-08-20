package com.motodigo.gateway.security;

import org.springframework.http.HttpStatus;

public enum GatewayErrorCode {
	AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "gateway.error.auth-required"),
	AUTH_INVALID(HttpStatus.UNAUTHORIZED, "gateway.error.auth-invalid"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "gateway.error.access-denied"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "gateway.error.rate-limited"),
	CALLBACK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "gateway.error.callback-signature-invalid"),
	CALLBACK_ORIGIN_DENIED(HttpStatus.UNAUTHORIZED, "gateway.error.callback-origin-denied"),
	CALLBACK_REPLAY(HttpStatus.CONFLICT, "gateway.error.callback-replay"),
	CALLBACK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "gateway.error.callback-unavailable");

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
