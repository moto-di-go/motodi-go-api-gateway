package com.motodigo.gateway.integrations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class CallbackIdempotencyStore {

	private static final String KEY_PREFIX = "gateway:callback:idem:";
	private static final String DONE = "done";
	private static final String PENDING_PREFIX = "pending:";

	private final StringRedisTemplate redisTemplate;

	CallbackIdempotencyStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	Optional<ClaimResult> tryClaim(String provider, String idempotencyKey, Duration pendingTtl) {
		String key = redisKey(provider, idempotencyKey);
		String existing = this.redisTemplate.opsForValue().get(key);
		if (existing != null) {
			return Optional.empty();
		}
		String token = UUID.randomUUID().toString();
		Boolean claimed = this.redisTemplate.opsForValue().setIfAbsent(key, PENDING_PREFIX + token, pendingTtl);
		if (!Boolean.TRUE.equals(claimed)) {
			return Optional.empty();
		}
		return Optional.of(new ClaimResult(token));
	}

	void markDone(String provider, String idempotencyKey, Duration ttl) {
		String key = redisKey(provider, idempotencyKey);
		this.redisTemplate.opsForValue().set(key, DONE, ttl);
	}

	void release(String provider, String idempotencyKey, String claimToken) {
		String key = redisKey(provider, idempotencyKey);
		String existing = this.redisTemplate.opsForValue().get(key);
		if (existing != null && existing.equals(PENDING_PREFIX + claimToken)) {
			this.redisTemplate.delete(key);
		}
	}

	private static String redisKey(String provider, String idempotencyKey) {
		return KEY_PREFIX + provider + ":" + idempotencyKey;
	}

	record ClaimResult(String token) {
	}
}
