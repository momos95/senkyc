package sn.senkyc.api.platform.ai;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import sn.senkyc.api.platform.web.CorrelationIdFilter;

/** Ajoute le jeton interne et l'identifiant de corrélation à chaque appel vers le service IA. */
final class AiServiceRequestInterceptor implements ClientHttpRequestInterceptor {

    private final String internalToken;

    AiServiceRequestInterceptor(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(internalToken);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
