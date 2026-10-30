package sn.senkyc.api.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import sn.senkyc.api.platform.web.CorrelationIdFilter;

class RestAiServiceClientTest {

    private MockRestServiceServer server;
    private RestAiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://ai.test")
                .requestInterceptor(new AiServiceRequestInterceptor("test-token"));
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestAiServiceClient(builder.build());
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void infoCallsAiServiceWithTokenAndCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-12345678");
        server.expect(requestTo("http://ai.test/v1/info"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(header(CorrelationIdFilter.HEADER, "corr-12345678"))
                .andRespond(withSuccess(
                        "{\"service\":\"senkyc-ai\",\"version\":\"0.1.0\",\"models\":{}}", MediaType.APPLICATION_JSON));

        AiServiceInfo info = client.info();

        assertThat(info.service()).isEqualTo("senkyc-ai");
        assertThat(info.version()).isEqualTo("0.1.0");
        assertThat(info.models()).isEmpty();
        server.verify();
    }

    @Test
    void unauthorizedResponseIsReportedAsUnavailable() {
        server.expect(requestTo("http://ai.test/v1/info")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::info).isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void serverErrorIsReportedAsUnavailable() {
        server.expect(requestTo("http://ai.test/v1/info")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(client::info).isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void networkFailureIsReportedAsUnavailable() {
        server.expect(requestTo("http://ai.test/v1/info")).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(client::info)
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageNotContaining("ai.test");
    }
}
