package com.motodigo.gateway.security;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.motodigo.gateway.config.GatewayProperties;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitingFilter.class);
	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	private final GatewayProperties properties;
	private final RedisTokenBucketRateLimiter rateLimiter;
	private final GatewayErrorWriter errorWriter;
	private final SecurityAuditLogger auditLogger;

	RateLimitingFilter(GatewayProperties properties, RedisTokenBucketRateLimiter rateLimiter,
			GatewayErrorWriter errorWriter, SecurityAuditLogger auditLogger) {
		this.properties = properties;
		this.rateLimiter = rateLimiter;
		this.errorWriter = errorWriter;
		this.auditLogger = auditLogger;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Policy policy = policyFor(request);
		if (policy == null || !this.properties.rateLimit().enabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			RateLimitResult result = this.rateLimiter.consume(rateLimitKey(request, policy), bucket(policy));
			writeRateLimitHeaders(response, result);
			if (result.allowed()) {
				filterChain.doFilter(request, response);
				return;
			}
			response.setHeader("Retry-After", Long.toString(Math.max(result.retryAfterSeconds(), 1)));
			this.auditLogger.warn(request, GatewayErrorCode.RATE_LIMITED, SecurityContextHolder.getContext().getAuthentication());
			this.errorWriter.write(request, response, GatewayErrorCode.RATE_LIMITED);
		}
		catch (RedisConnectionFailureException | IllegalStateException ex) {
			LOGGER.error("gateway_rate_limit_unavailable method={} path={} ip={}", request.getMethod(), request.getRequestURI(),
					clientIp(request));
			GatewayProperties.Bucket bucket = bucket(policy);
			response.setHeader("Retry-After", Long.toString(bucket.refillPeriodSeconds()));
			response.setHeader("X-RateLimit-Limit", Long.toString(bucket.capacity()));
			response.setHeader("X-RateLimit-Remaining", "0");
			response.setHeader("X-RateLimit-Reset", Long.toString((System.currentTimeMillis() / 1000) + bucket.refillPeriodSeconds()));
			this.auditLogger.warn(request, GatewayErrorCode.RATE_LIMITED, SecurityContextHolder.getContext().getAuthentication());
			this.errorWriter.write(request, response, GatewayErrorCode.RATE_LIMITED);
		}
	}

	private GatewayProperties.Bucket bucket(Policy policy) {
		return switch (policy) {
			case AUTH -> this.properties.rateLimit().auth();
			case OTP -> this.properties.rateLimit().otp();
			case PAYMENT -> this.properties.rateLimit().payment();
			case CALLBACK -> this.properties.rateLimit().callback();
			case GPS -> this.properties.rateLimit().gps();
		};
	}

	private static Policy policyFor(HttpServletRequest request) {
		String method = request.getMethod().toUpperCase(Locale.ROOT);
		String path = request.getRequestURI();
		if ((HttpMethod.PUT.matches(method) || HttpMethod.PATCH.matches(method))
				&& matchesAny(path, "/api/v1/drivers/position")) {
			return Policy.GPS;
		}
		if (!HttpMethod.POST.matches(method) && !HttpMethod.PUT.matches(method)) {
			return null;
		}
		if (matchesAny(path, "/api/v1/auth/otp/verify", "/api/v1/auth/otp/resend")) {
			return Policy.OTP;
		}
		if (matchesAny(path,
				"/api/v1/auth/register",
				"/api/v1/auth/login",
				"/api/v1/auth/refresh",
				"/api/v1/auth/password/forgot",
				"/api/v1/auth/password/reset")) {
			return Policy.AUTH;
		}
		if (matchesAny(path,
				"/api/v1/wallet/deposits",
				"/api/v1/bookings/*/pay",
				"/api/v1/nfc/tokens")) {
			return Policy.PAYMENT;
		}
		if (matchesAny(path,
				"/api/v1/integrations/mobile-money/callback",
				"/api/v1/integrations/mobile-money/*/callback")) {
			return Policy.CALLBACK;
		}
		return null;
	}

	private static boolean matchesAny(String path, String... patterns) {
		for (String pattern : patterns) {
			if (PATH_MATCHER.match(pattern, path)) {
				return true;
			}
		}
		return false;
	}

	private static String rateLimitKey(HttpServletRequest request, Policy policy) {
		String identity = switch (policy) {
			case PAYMENT, GPS -> authenticatedIdentity(request);
			case AUTH, OTP, CALLBACK -> clientIp(request);
		};
		return "motodigo:gateway:rate-limit:" + policy.name().toLowerCase(Locale.ROOT) + ":" + identity;
	}

	private static String authenticatedIdentity(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication
				&& StringUtils.hasText(jwtAuthentication.getToken().getSubject())) {
			return "user:" + SecurityAuditLogger.subjectHash(jwtAuthentication.getToken().getSubject());
		}
		return "ip:" + clientIp(request);
	}

	private static String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwardedFor)) {
			return forwardedFor.split(",", 2)[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static void writeRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
		response.setHeader("X-RateLimit-Limit", Long.toString(result.limit()));
		response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(result.remainingTokens(), 0)));
		response.setHeader("X-RateLimit-Reset", Long.toString(result.resetEpochSeconds()));
	}

	private enum Policy {
		AUTH,
		OTP,
		PAYMENT,
		CALLBACK,
		GPS
	}
}
