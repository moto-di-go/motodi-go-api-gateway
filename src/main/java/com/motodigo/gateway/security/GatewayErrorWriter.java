package com.motodigo.gateway.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import com.motodigo.gateway.web.CorrelationIdFilter;

@Component
class GatewayErrorWriter {

	private final ObjectMapper objectMapper;
	private final MessageSource messageSource;
	private final LocaleResolver localeResolver;

	GatewayErrorWriter(ObjectMapper objectMapper, MessageSource messageSource, LocaleResolver localeResolver) {
		this.objectMapper = objectMapper;
		this.messageSource = messageSource;
		this.localeResolver = localeResolver;
	}

	void write(HttpServletRequest request, HttpServletResponse response, GatewayErrorCode errorCode) throws IOException {
		if (response.isCommitted()) {
			return;
		}
		Locale locale = this.localeResolver.resolveLocale(request);
		String message = this.messageSource.getMessage(errorCode.messageKey(), null, locale);
		String correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
		}
		response.setStatus(errorCode.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.objectMapper.writeValue(response.getOutputStream(),
				new GatewayErrorResponse(errorCode.name(), message, correlationId, Instant.now()));
	}
}
