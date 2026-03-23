package com.deskit.deskit.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BroadcastJoinLeaveTraceLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String PERF_PHASE_HEADER = "X-Perf-Phase";
    private static final String UNKNOWN = "unknown";
    private static final Pattern TARGET_PATH = Pattern.compile("^/api/broadcasts/\\d+/(join|leave)$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !TARGET_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String path = request.getRequestURI();
        String method = request.getMethod();

        String traceId = normalizeHeaderOrDefault(request.getHeader(TRACE_ID_HEADER), UUID.randomUUID().toString());
        String perfPhase = normalizeHeaderOrDefault(request.getHeader(PERF_PHASE_HEADER), UNKNOWN);

        MDC.put("trace_id", traceId);
        MDC.put("perf_phase", perfPhase);
        try {
            filterChain.doFilter(request, response);
        } finally {
            double appTotalMs = (System.nanoTime() - startNanos) / 1_000_000.0;
            int status = response.getStatus();

            log.info(
                    "perf_trace path={} method={} status={} app_total_ms={} trace_id={} perf_phase={}",
                    path,
                    method,
                    status,
                    String.format("%.3f", appTotalMs),
                    traceId,
                    perfPhase
            );
            MDC.remove("trace_id");
            MDC.remove("perf_phase");
        }
    }

    private String normalizeHeaderOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
