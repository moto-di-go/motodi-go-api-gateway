package com.motodigo.gateway.security;

import java.time.Instant;

record GatewayErrorResponse(String errorCode, String message, String correlationId, Instant timestamp) {
}
