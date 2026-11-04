package sn.senkyc.api.platform.audit;

import java.util.Map;

/**
 * Journal d'audit en ajout seul. Le tenant, l'auteur (sujet du jeton) et l'identifiant de corrélation sont
 * déduits du contexte courant. Ne jamais y mettre de donnée d'identité ni de contenu biométrique.
 */
public interface AuditLog {

    void record(String action, String resourceType, String resourceId, Map<String, Object> details);
}
