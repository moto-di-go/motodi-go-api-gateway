package com.motodigo.gateway.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Flushes the response after each write for SSE proxied through Spring Cloud Gateway MVC,
 * so clients receive events without waiting for the default streaming buffer to fill.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SseFlushFilter extends OncePerRequestFilter {

	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String accept = request.getHeader(HttpHeaders.ACCEPT);
		if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
			return false;
		}
		return !PATH_MATCHER.match("/api/v1/realtime/sse", request.getRequestURI())
				&& !PATH_MATCHER.match("/api/v1/realtime/sse/**", request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		filterChain.doFilter(request, new FlushingSseResponse(response));
	}

	private static final class FlushingSseResponse extends HttpServletResponseWrapper {

		private ServletOutputStream outputStream;

		private FlushingSseResponse(HttpServletResponse response) {
			super(response);
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			if (this.outputStream == null) {
				ServletOutputStream delegate = super.getOutputStream();
				this.outputStream = new ServletOutputStream() {
					@Override
					public boolean isReady() {
						return delegate.isReady();
					}

					@Override
					public void setWriteListener(WriteListener listener) {
						delegate.setWriteListener(listener);
					}

					@Override
					public void write(int b) throws IOException {
						delegate.write(b);
						flush();
					}

					@Override
					public void write(byte[] b, int off, int len) throws IOException {
						delegate.write(b, off, len);
						flush();
					}

					@Override
					public void flush() throws IOException {
						delegate.flush();
					}

					@Override
					public void close() throws IOException {
						delegate.close();
					}
				};
			}
			return this.outputStream;
		}
	}
}
