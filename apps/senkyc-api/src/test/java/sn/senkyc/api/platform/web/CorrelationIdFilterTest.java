package sn.senkyc.api.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String incomingHeader, AtomicReference<String> mdcSeenByChain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incomingHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> mdcSeenByChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void reusesWellFormedIncomingId() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String responseId = run("client-corr-12345", seen);

        assertThat(responseId).isEqualTo("client-corr-12345");
        assertThat(seen.get()).isEqualTo("client-corr-12345");
    }

    @Test
    void generatesIdWhenMissing() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String responseId = run(null, seen);

        assertThat(responseId).isNotBlank();
        assertThat(seen.get()).isEqualTo(responseId);
    }

    @Test
    void replacesMalformedIncomingId() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String responseId = run("bad id\nforged log line", seen);

        assertThat(responseId).doesNotContain("\n").doesNotContain(" ");
        assertThat(seen.get()).isEqualTo(responseId);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run("client-corr-12345", new AtomicReference<>());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
