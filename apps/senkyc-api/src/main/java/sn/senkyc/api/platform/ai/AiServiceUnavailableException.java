package sn.senkyc.api.platform.ai;

/** Le service IA n'a pas pu répondre correctement. L'appelant doit traiter ce cas en fail-closed. */
public class AiServiceUnavailableException extends RuntimeException {

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
