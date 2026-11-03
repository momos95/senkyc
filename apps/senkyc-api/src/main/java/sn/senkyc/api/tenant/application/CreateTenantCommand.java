package sn.senkyc.api.tenant.application;

/** @param retentionDays durée de rétention des documents en jours ; {@code null} = valeur par défaut */
public record CreateTenantCommand(String code, String name, Integer retentionDays) {}
