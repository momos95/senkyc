package sn.senkyc.api.platform.ai;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/** Santé du service IA, obtenue par un véritable appel HTTP authentifié vers {@code /v1/info}. */
public class AiServiceHealthIndicator implements HealthIndicator {

    private final AiServiceClient client;

    public AiServiceHealthIndicator(AiServiceClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            AiServiceInfo info = client.info();
            return Health.up()
                    .withDetail("service", String.valueOf(info.service()))
                    .withDetail("version", String.valueOf(info.version()))
                    .build();
        } catch (AiServiceUnavailableException e) {
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
    }
}
