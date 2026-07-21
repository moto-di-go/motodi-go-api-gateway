package com.motodigo.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.motodigo.gateway.web.CorrelationIdFilter;

@Component
class SecurityAuditLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditLogger.class);

	void warn(HttpServletRequest request, GatewayErrorCode code, Authentication authentication) {
		LOGGER.warn("gateway_security_event errorCode={} method={} path={} ip={} correlationId={} userHash={}",
				code.name(),
				request.getMethod(),
				request.getRequestURI(),
				clientIp(request),
				correlationId(request),
				userHash(authentication));
	}

	private static String correlationId(HttpServletRequest request) {
		String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
		return StringUtils.hasText(correlationId) ? correlationId : "-";
	}

	private static String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwardedFor)) {
			return forwardedFor.split(",", 2)[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static String userHash(Authentication authentication) {
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || authentication.getName() == null) {
			return "-";
		}
		return sha256(jwtAuthentication.getName()).substring(0, 16);
	}

	static String subjectHash(String subject) {
		if (!StringUtils.hasText(subject)) {
			return "-";
		}
		return sha256(subject).substring(0, 16);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
