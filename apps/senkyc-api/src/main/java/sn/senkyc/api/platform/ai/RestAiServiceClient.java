package sn.senkyc.api.platform.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptateur HTTP (RestClient) du port {@link AiServiceClient}. */
public class RestAiServiceClient implements AiServiceClient {

    private final RestClient restClient;

    public RestAiServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AiServiceInfo info() {
        try {
            AiServiceInfo info = restClient
                    .get()
                    .uri("/v1/info")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(AiServiceInfo.class);
            if (info == null) {
                throw new AiServiceUnavailableException("AI service returned an empty response", null);
            }
            return info;
        } catch (RestClientException e) {
            // Le message d'origine peut contenir l'URL ; on ne conserve que le type d'erreur.
            throw new AiServiceUnavailableException(
                    "AI service call failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
