package com.motodigo.gateway.integrations;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;

import com.motodigo.gateway.web.CorrelationIdFilter;

@RestController
class AiFallbackController {

	private static final Logger LOGGER = LoggerFactory.getLogger(AiFallbackController.class);

	private final MessageSource messageSource;
	private final LocaleResolver localeResolver;
	private final MeterRegistry meterRegistry;

	AiFallbackController(MessageSource messageSource, LocaleResolver localeResolver, MeterRegistry meterRegistry) {
		this.messageSource = messageSource;
		this.localeResolver = localeResolver;
		this.meterRegistry = meterRegistry;
	}

	@RequestMapping(path = "/__gateway/ai/fallback", produces = MediaType.APPLICATION_JSON_VALUE)
	AiFallbackResponse fallback(HttpServletRequest request) {
		this.meterRegistry.counter("gateway.ai.fallback").increment();
		Locale locale = this.localeResolver.resolveLocale(request);
		String message = this.messageSource.getMessage("gateway.ai.fallback-message", null, locale);
		String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
		LOGGER.warn("gateway_ai_fallback correlationId={}",
				correlationId == null || correlationId.isBlank() ? "-" : correlationId);
		return new AiFallbackResponse(List.of(), true, message, Instant.now(), 0.0);
	}

	record AiFallbackResponse(
			List<Object> recommendations,
			boolean fallback,
			String message,
			Instant generated_at,
			double latency_ms) {
	}
}
