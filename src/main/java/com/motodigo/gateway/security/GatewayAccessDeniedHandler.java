package com.motodigo.gateway.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
class GatewayAccessDeniedHandler implements AccessDeniedHandler {

	private final GatewayErrorWriter errorWriter;
	private final SecurityAuditLogger auditLogger;

	GatewayAccessDeniedHandler(GatewayErrorWriter errorWriter, SecurityAuditLogger auditLogger) {
		this.errorWriter = errorWriter;
		this.auditLogger = auditLogger;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException, ServletException {
		this.auditLogger.warn(request, GatewayErrorCode.ACCESS_DENIED, SecurityContextHolder.getContext().getAuthentication());
		this.errorWriter.write(request, response, GatewayErrorCode.ACCESS_DENIED);
	}
}
