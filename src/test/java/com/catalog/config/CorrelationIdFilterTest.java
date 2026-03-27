package com.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesCorrelationId_whenHeaderMissing() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String responseHeader = response.getHeader("X-Correlation-ID");
        assertThat(responseHeader).isNotNull().isNotBlank();
        assertThat(responseHeader).matches("[0-9a-f\\-]{36}");
    }

    @Test
    void preservesCorrelationId_whenHeaderProvided() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "my-custom-id-123");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("my-custom-id-123");
    }

    @Test
    void ignoresBlankHeader() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "   ");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String responseHeader = response.getHeader("X-Correlation-ID");
        assertThat(responseHeader).isNotBlank();
        assertThat(responseHeader).isNotEqualTo("   ");
    }

    @Test
    void clearsMdc_afterFilterChain() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void setsMdc_duringFilterChain() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "test-trace-id");
        var response = new MockHttpServletResponse();
        String[] capturedMdc = new String[1];
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                capturedMdc[0] = MDC.get("correlationId");
            }
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(capturedMdc[0]).isEqualTo("test-trace-id");
    }
}
