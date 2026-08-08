package com.motodigo.gateway.integrations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.motodigo.gateway.config.GatewayProperties;
import com.motodigo.gateway.security.GatewayErrorCode;
import com.motodigo.gateway.security.GatewayErrorWriter;
import com.motodigo.gateway.security.SecurityAuditLogger;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MobileMoneyCallbackFilter extends OncePerRequestFilter {

	static final String SIGNATURE_HEADER = "X-Signature";
	static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
	static final String PROVIDER_HEADER = "X-Provider";

	private static final Logger LOGGER = LoggerFactory.getLogger(MobileMoneyCallbackFilter.class);
	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
	private static final String CALLBACK_PATH_PATTERN = "/api/v1/integrations/mobile-money/**/callback";
	private static final String LEGACY_CALLBACK_PATH = "/api/v1/integrations/mobile-money/callback";

	private final GatewayProperties properties;
	private final CallbackIdempotencyStore idempotencyStore;
	private final GatewayErrorWriter errorWriter;
	private final SecurityAuditLogger auditLogger;
	private final MeterRegistry meterRegistry;

	MobileMoneyCallbackFilter(GatewayProperties properties, CallbackIdempotencyStore idempotencyStore,
			GatewayErrorWriter errorWriter, SecurityAuditLogger auditLogger, MeterRegistry meterRegistry) {
		this.properties = properties;
		this.idempotencyStore = idempotencyStore;
		this.errorWriter = errorWriter;
		this.auditLogger = auditLogger;
		this.meterRegistry = meterRegistry;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!this.properties.integrations().mobileMoney().enabled()) {
			return true;
		}
		if (!HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}
		String path = request.getRequestURI();
		return !(PATH_MATCHER.match(CALLBACK_PATH_PATTERN, path) || LEGACY_CALLBACK_PATH.equals(path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String provider = resolveProvider(request);
		GatewayProperties.Provider providerConfig = this.properties.integrations().mobileMoney().provider(provider);
		if (providerConfig == null) {
			reject(request, response, provider, GatewayErrorCode.CALLBACK_SIGNATURE_INVALID, "unknown_provider");
			return;
		}

		byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
		if (!isOriginAllowed(clientIp(request), providerConfig)) {
			reject(request, response, provider, GatewayErrorCode.CALLBACK_ORIGIN_DENIED, "origin");
			return;
		}
		if (!isSignatureValid(body, request.getHeader(SIGNATURE_HEADER), providerConfig.hmacSecret())) {
			reject(request, response, provider, GatewayErrorCode.CALLBACK_SIGNATURE_INVALID, "signature");
			return;
		}

		String idempotencyKey = resolveIdempotencyKey(request, body);
		String claimToken = null;
		try {
			Optional<CallbackIdempotencyStore.ClaimResult> claim = this.idempotencyStore.tryClaim(provider, idempotencyKey,
					this.properties.integrations().mobileMoney().pendingTtl());
			if (claim.isEmpty()) {
				reject(request, response, provider, GatewayErrorCode.CALLBACK_REPLAY, "replay");
				return;
			}
			claimToken = claim.get().token();

			StatusCapturingResponseWrapper responseWrapper = new StatusCapturingResponseWrapper(response);
			filterChain.doFilter(new CachedBodyRequest(request, body), responseWrapper);

			if (responseWrapper.status() >= 200 && responseWrapper.status() < 300) {
				this.idempotencyStore.markDone(provider, idempotencyKey,
						this.properties.integrations().mobileMoney().idempotencyTtl());
				this.meterRegistry.counter("gateway.callback.accepted", "provider", provider).increment();
			}
			else {
				this.idempotencyStore.release(provider, idempotencyKey, claimToken);
				this.meterRegistry.counter("gateway.callback.rejected", "provider", provider, "reason", "downstream")
						.increment();
			}
		}
		catch (RedisConnectionFailureException | IllegalStateException ex) {
			LOGGER.error("gateway_callback_idempotency_unavailable provider={} path={}", provider, request.getRequestURI());
			if (claimToken != null) {
				try {
					this.idempotencyStore.release(provider, idempotencyKey, claimToken);
				}
				catch (RuntimeException ignored) {
					// best effort
				}
			}
			reject(request, response, provider, GatewayErrorCode.CALLBACK_UNAVAILABLE, "redis");
		}
	}

	private void reject(HttpServletRequest request, HttpServletResponse response, String provider,
			GatewayErrorCode errorCode, String reason) throws IOException {
		this.meterRegistry.counter("gateway.callback.rejected", "provider", provider, "reason", reason).increment();
		this.auditLogger.warn(request, errorCode, SecurityContextHolder.getContext().getAuthentication());
		this.errorWriter.write(request, response, errorCode);
	}

	static String resolveProvider(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (LEGACY_CALLBACK_PATH.equals(path)) {
			String headerProvider = request.getHeader(PROVIDER_HEADER);
			if (StringUtils.hasText(headerProvider)) {
				return headerProvider.trim().toLowerCase(Locale.ROOT);
			}
			return "aggregator";
		}
		String[] segments = path.split("/");
		// /api/v1/integrations/mobile-money/{provider}/callback
		if (segments.length >= 6) {
			return segments[5].toLowerCase(Locale.ROOT);
		}
		return "aggregator";
	}

	static String resolveIdempotencyKey(HttpServletRequest request, byte[] body) {
		String headerKey = request.getHeader(IDEMPOTENCY_HEADER);
		if (StringUtils.hasText(headerKey)) {
			return headerKey.trim();
		}
		return sha256Hex(body);
	}

	static boolean isSignatureValid(byte[] body, String providedSignature, String secret) {
		if (!StringUtils.hasText(providedSignature) || !StringUtils.hasText(secret)) {
			return false;
		}
		String expected = hmacSha256Hex(body, secret);
		String normalized = providedSignature.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("sha256=")) {
			normalized = normalized.substring("sha256=".length());
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				normalized.getBytes(StandardCharsets.UTF_8));
	}

	static String hmacSha256Hex(byte[] body, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
		}
	}

	static boolean isOriginAllowed(String clientIp, GatewayProperties.Provider provider) {
		if (provider.allowedSourceIps() == null || provider.allowedSourceIps().isEmpty()) {
			return true;
		}
		for (String allowed : provider.allowedSourceIps()) {
			try {
				if (new IpAddressMatcher(allowed).matches(clientIp)) {
					return true;
				}
			}
			catch (IllegalArgumentException ignored) {
				// skip invalid allowlist entries
			}
		}
		return false;
	}

	private static String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwardedFor)) {
			return forwardedFor.split(",", 2)[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static String sha256Hex(byte[] value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		private CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(this.body);
			return new ServletInputStream() {
				@Override
				public boolean isFinished() {
					return inputStream.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener readListener) {
					// no-op
				}

				@Override
				public int read() {
					return inputStream.read();
				}
			};
		}

		@Override
		public int getContentLength() {
			return this.body.length;
		}

		@Override
		public long getContentLengthLong() {
			return this.body.length;
		}
	}

	private static final class StatusCapturingResponseWrapper extends HttpServletResponseWrapper {

		private int status = HttpServletResponse.SC_OK;

		private StatusCapturingResponseWrapper(HttpServletResponse response) {
			super(response);
		}

		@Override
		public void setStatus(int sc) {
			this.status = sc;
			super.setStatus(sc);
		}

		@Override
		public void sendError(int sc) throws IOException {
			this.status = sc;
			super.sendError(sc);
		}

		@Override
		public void sendError(int sc, String msg) throws IOException {
			this.status = sc;
			super.sendError(sc, msg);
		}

		private int status() {
			return this.status;
		}
	}
}
