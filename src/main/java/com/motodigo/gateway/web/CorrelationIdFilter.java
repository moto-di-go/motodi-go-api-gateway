package com.motodigo.gateway.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Correlation-Id";
	private static final String MDC_KEY = "correlationId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String correlationId = request.getHeader(HEADER_NAME);
		if (!StringUtils.hasText(correlationId)) {
			correlationId = UUID.randomUUID().toString();
		}
		else {
			correlationId = correlationId.trim();
		}

		response.setHeader(HEADER_NAME, correlationId);
		MDC.put(MDC_KEY, correlationId);

		try {
			filterChain.doFilter(new CorrelationIdRequestWrapper(request, correlationId), response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	private static final class CorrelationIdRequestWrapper extends HttpServletRequestWrapper {

		private final String correlationId;

		private CorrelationIdRequestWrapper(HttpServletRequest request, String correlationId) {
			super(request);
			this.correlationId = correlationId;
		}

		@Override
		public String getHeader(String name) {
			if (HEADER_NAME.equalsIgnoreCase(name)) {
				return this.correlationId;
			}
			return super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if (HEADER_NAME.equalsIgnoreCase(name)) {
				return Collections.enumeration(List.of(this.correlationId));
			}
			return super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = Collections.list(super.getHeaderNames());
			boolean hasCorrelationHeader = names.stream().anyMatch(HEADER_NAME::equalsIgnoreCase);
			if (!hasCorrelationHeader) {
				names = new ArrayList<>(names);
				names.add(HEADER_NAME);
			}
			return Collections.enumeration(names);
		}
	}
}
