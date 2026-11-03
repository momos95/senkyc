package sn.senkyc.api.tenant.adapter.in.web;

import java.time.Instant;
import java.util.UUID;
import sn.senkyc.api.tenant.domain.Tenant;

record TenantResponse(UUID id, String code, String name, String status, int retentionDays, Instant createdAt) {

    static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.id(),
                tenant.code(),
                tenant.name(),
                tenant.status().name(),
                tenant.retentionDays(),
                tenant.createdAt());
    }
}
