package sn.senkyc.api.platform.ai;

/**
 * Port sortant vers le service d'inférence IA (Python/FastAPI).
 * Les cas d'usage métier dépendent de cette interface, jamais du client HTTP.
 */
public interface AiServiceClient {

    /**
     * Interroge {@code GET /v1/info} (authentifié).
     *
     * @throws AiServiceUnavailableException si le service est injoignable, en erreur ou refuse l'appel
     */
    AiServiceInfo info();
}
