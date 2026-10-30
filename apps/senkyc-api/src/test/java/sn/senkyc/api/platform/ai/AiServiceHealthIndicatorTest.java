package sn.senkyc.api.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class AiServiceHealthIndicatorTest {

    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(client);

    @Test
    void isUpWhenAiServiceAnswers() {
        when(client.info()).thenReturn(new AiServiceInfo("senkyc-ai", "0.1.0", Map.of()));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("version", "0.1.0");
    }

    @Test
    void isDownWhenAiServiceIsUnavailable() {
        when(client.info()).thenThrow(new AiServiceUnavailableException("AI service call failed", null));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
