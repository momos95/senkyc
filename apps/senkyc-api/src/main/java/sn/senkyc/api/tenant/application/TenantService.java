package sn.senkyc.api.tenant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import sn.senkyc.api.platform.pagination.PageResult;
import sn.senkyc.api.platform.tenant.TenantContext;
import sn.senkyc.api.tenant.domain.Tenant;
import sn.senkyc.api.tenant.domain.TenantNotFoundException;
import sn.senkyc.api.tenant.domain.TenantStatus;

@Service
public class TenantService {

    /** Valeur provisoire, à valider avec le DPO / le cadre juridique applicable. */
    public static final int DEFAULT_RETENTION_DAYS = 30;

    private final TenantRepository repository;
    private final Clock clock;

    public TenantService(TenantRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Le tenant du jeton courant. @throws sn.senkyc.api.platform.tenant.TenantContextMissingException sans tenant */
    public Tenant currentTenant() {
        UUID id = TenantContext.require();
        return repository.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    public Tenant createTenant(CreateTenantCommand command) {
        int retention = command.retentionDays() != null ? command.retentionDays() : DEFAULT_RETENTION_DAYS;
        Tenant tenant = new Tenant(
                UUID.randomUUID(),
                command.code(),
                command.name(),
                TenantStatus.ACTIVE,
                retention,
                false,
                Instant.now(clock));
        return repository.create(tenant);
    }

    public PageResult<Tenant> list(int page, int size) {
        long offset = (long) page * size;
        List<Tenant> items = repository.findAll(offset, size);
        return new PageResult<>(items, page, size, repository.count());
    }
}
