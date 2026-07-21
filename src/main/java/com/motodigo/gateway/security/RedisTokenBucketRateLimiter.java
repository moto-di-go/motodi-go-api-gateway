package com.motodigo.gateway.security;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.motodigo.gateway.config.GatewayProperties;

@Component
class RedisTokenBucketRateLimiter {

	private static final String LUA_SCRIPT = """
			local key = KEYS[1]
			local now = tonumber(ARGV[1])
			local capacity = tonumber(ARGV[2])
			local refillTokens = tonumber(ARGV[3])
			local refillPeriodMillis = tonumber(ARGV[4])
			local requested = tonumber(ARGV[5])
			local ttlMillis = tonumber(ARGV[6])

			local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
			local tokens = tonumber(bucket[1])
			local timestamp = tonumber(bucket[2])
			if tokens == nil then
			  tokens = capacity
			  timestamp = now
			end

			local elapsed = math.max(0, now - timestamp)
			local refillPeriods = math.floor(elapsed / refillPeriodMillis)
			if refillPeriods > 0 then
			  tokens = math.min(capacity, tokens + (refillPeriods * refillTokens))
			  timestamp = timestamp + (refillPeriods * refillPeriodMillis)
			end

			local allowed = 0
			local retryAfterSeconds = 0
			if tokens >= requested then
			  allowed = 1
			  tokens = tokens - requested
			else
			  retryAfterSeconds = math.ceil(((requested - tokens) * refillPeriodMillis) / refillTokens / 1000)
			end

			redis.call('HSET', key, 'tokens', tokens, 'timestamp', timestamp)
			redis.call('PEXPIRE', key, ttlMillis)
			local resetEpochSeconds = math.ceil((timestamp + refillPeriodMillis) / 1000)
			return { allowed, tokens, retryAfterSeconds, capacity, resetEpochSeconds }
			""";

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;
	private final DefaultRedisScript<List> script;

	@Autowired
	RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
		this(redisTemplate, Clock.systemUTC());
	}

	RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate, Clock clock) {
		this.redisTemplate = redisTemplate;
		this.clock = clock;
		this.script = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
	}

	RateLimitResult consume(String key, GatewayProperties.Bucket bucket) {
		long nowMillis = this.clock.millis();
		long refillPeriodMillis = bucket.refillPeriodSeconds() * 1000;
		long ttlMillis = Math.max(refillPeriodMillis * 2, 1000);
		@SuppressWarnings("unchecked")
		List<Long> values = this.redisTemplate.execute(this.script, List.of(key),
				Long.toString(nowMillis),
				Long.toString(bucket.capacity()),
				Long.toString(bucket.refillTokens()),
				Long.toString(refillPeriodMillis),
				"1",
				Long.toString(ttlMillis));
		if (values == null || values.size() < 5) {
			throw new IllegalStateException("Redis rate limit script returned no result");
		}
		return new RateLimitResult(values.get(0) == 1L, values.get(1), values.get(2), values.get(3), values.get(4));
	}
}
