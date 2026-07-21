package com.motodigo.gateway.security;

record RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds, long limit, long resetEpochSeconds) {
}
