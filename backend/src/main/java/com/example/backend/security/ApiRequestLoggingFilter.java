package com.example.backend.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * リクエスト単位の構造化ログと簡易メトリクスを収集するフィルタ。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public ApiRequestLoggingFilter(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = resolveRequestId(request);
        String clientIp = resolveClientIp(request);

        response.setHeader("X-Request-Id", requestId);
        MDC.put("requestId", requestId);

        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long durationNanos = System.nanoTime() - startedAt;
            status = response.getStatus();

            recordMetrics(request, status, durationNanos);
            writeStructuredLog(request, status, durationNanos, requestId, clientIp);
            MDC.remove("requestId");
        }
    }

    private void recordMetrics(HttpServletRequest request, int status, long durationNanos) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        meterRegistry.timer("app.api.request.duration",
                        Tags.of(
                                "method", method,
                                "path", normalizePath(path),
                                "status", Integer.toString(status)
                        ))
                .record(durationNanos, TimeUnit.NANOSECONDS);

        if (status >= 500) {
            meterRegistry.counter("app.api.request.errors", "method", method, "path", normalizePath(path)).increment();
        }
    }

    private void writeStructuredLog(
            HttpServletRequest request,
            int status,
            long durationNanos,
            String requestId,
            String clientIp
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("requestId", requestId);
        payload.put("method", request.getMethod());
        payload.put("path", request.getRequestURI());
        payload.put("query", request.getQueryString());
        payload.put("status", status);
        payload.put("durationMs", TimeUnit.NANOSECONDS.toMillis(durationNanos));
        payload.put("clientIp", clientIp);

        try {
            log.info("{}", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.info("requestId={} method={} path={} status={} durationMs={} clientIp={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    TimeUnit.NANOSECONDS.toMillis(durationNanos),
                    clientIp
            );
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return value.trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] values = forwardedFor.split(",");
            return values[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.replaceAll("/[0-9]+", "/{id}");
    }
}
