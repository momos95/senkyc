package sn.senkyc.api.tenant.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import sn.senkyc.api.tenant.domain.Tenant;

/** Port sortant de persistance des tenants. */
public interface TenantRepository {

    Optional<Tenant> findById(UUID id);

    /** @throws sn.senkyc.api.tenant.domain.DuplicateTenantCodeException si le code existe déjà */
    Tenant create(Tenant tenant);

    List<Tenant> findAll(long offset, int limit);

    long count();
}
