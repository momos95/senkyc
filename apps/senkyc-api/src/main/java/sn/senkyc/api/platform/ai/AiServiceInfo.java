package sn.senkyc.api.platform.ai;

import java.util.Map;

/**
 * Description du service IA.
 *
 * @param models état de chargement par modèle (vide tant qu'aucun modèle n'est embarqué)
 */
public record AiServiceInfo(String service, String version, Map<String, String> models) {

    public AiServiceInfo {
        models = models == null ? Map.of() : Map.copyOf(models);
    }
}
