package com.motodigo.gateway.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class GatewayAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final GatewayErrorWriter errorWriter;
	private final SecurityAuditLogger auditLogger;

	GatewayAuthenticationEntryPoint(GatewayErrorWriter errorWriter, SecurityAuditLogger auditLogger) {
		this.errorWriter = errorWriter;
		this.auditLogger = auditLogger;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {
		GatewayErrorCode code = StringUtils.hasText(request.getHeader("Authorization"))
				? GatewayErrorCode.AUTH_INVALID
				: GatewayErrorCode.AUTH_REQUIRED;
		this.auditLogger.warn(request, code, null);
		this.errorWriter.write(request, response, code);
	}
}
