package br.com.hubregistro.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reusesValidIncomingIdAndExposesItDuringTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "bank-123.abc_X");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInMdc.set(MDC.get(CorrelationIds.MDC_KEY)));

        assertThat(seenInMdc.get()).isEqualTo("bank-123.abc_X");
        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("bank-123.abc_X");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).as("MDC limpo após a requisição").isNull();
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "abc\r\nInjected: log-line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(CorrelationIds.HEADER);
        assertThat(generated).isNotEqualTo("abc\r\nInjected: log-line");
        assertThat(CorrelationIds.isValid(generated)).isTrue();
    }

    @Test
    void generatesIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> { });

        assertThat(CorrelationIds.isValid(response.getHeader(CorrelationIds.HEADER))).isTrue();
    }

    @Test
    void rejectsIdsLongerThan64Characters() {
        assertThat(CorrelationIds.isValid("a".repeat(64))).isTrue();
        assertThat(CorrelationIds.isValid("a".repeat(65))).isFalse();
    }
}
