package com.deskit.deskit.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastJoinLeaveTraceLoggingFilterTest {

    private final BroadcastJoinLeaveTraceLoggingFilter filter = new BroadcastJoinLeaveTraceLoggingFilter();

    @Test
    void joinPathSetsMdcFromHeadersAndClearsAfterCompletion() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/broadcasts/123/join");
        request.addHeader("X-Trace-Id", "k6-trace-join-1");
        request.addHeader("X-Perf-Phase", "join_only");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("trace_id")).isEqualTo("k6-trace-join-1");
            assertThat(MDC.get("perf_phase")).isEqualTo("join_only");
            ((MockHttpServletResponse) res).setStatus(200);
        });

        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("perf_phase")).isNull();
    }

    @Test
    void missingHeadersUseFallbackValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/broadcasts/999/leave");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("trace_id")).isNotBlank();
            assertThat(MDC.get("perf_phase")).isEqualTo("unknown");
            ((MockHttpServletResponse) res).setStatus(200);
        });

        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("perf_phase")).isNull();
    }

    @Test
    void nonTargetPathIsSkipped() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/broadcasts/123/vod/view");
        request.addHeader("X-Trace-Id", "k6-trace");
        request.addHeader("X-Perf-Phase", "join_leave");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("trace_id")).isNull();
            assertThat(MDC.get("perf_phase")).isNull();
            ((MockHttpServletResponse) res).setStatus(200);
        });
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("perf_phase")).isNull();
    }
}
