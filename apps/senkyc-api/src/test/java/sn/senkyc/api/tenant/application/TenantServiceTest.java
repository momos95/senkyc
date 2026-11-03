package sn.senkyc.api.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sn.senkyc.api.platform.tenant.TenantContext;
import sn.senkyc.api.platform.tenant.TenantContextMissingException;
import sn.senkyc.api.tenant.domain.DuplicateTenantCodeException;
import sn.senkyc.api.tenant.domain.Tenant;
import sn.senkyc.api.tenant.domain.TenantNotFoundException;
import sn.senkyc.api.tenant.domain.TenantStatus;

class TenantServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final InMemoryTenantRepository repository = new InMemoryTenantRepository();
    private final TenantService service = new TenantService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsAnActiveTenantWithTheDefaultRetention() {
        Tenant tenant = service.createTenant(new CreateTenantCommand("bank-x", "Bank X", null));

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.retentionDays()).isEqualTo(TenantService.DEFAULT_RETENTION_DAYS);
        assertThat(tenant.createdAt()).isEqualTo(NOW);
        assertThat(repository.findById(tenant.id())).contains(tenant);
    }

    @Test
    void keepsAnExplicitRetention() {
        assertThat(service.createTenant(new CreateTenantCommand("bank-y", "Bank Y", 90)).retentionDays())
                .isEqualTo(90);
    }

    @Test
    void rejectsADuplicateCode() {
        service.createTenant(new CreateTenantCommand("bank-x", "Bank X", null));

        assertThatThrownBy(() -> service.createTenant(new CreateTenantCommand("bank-x", "Other", null)))
                .isInstanceOf(DuplicateTenantCodeException.class);
    }

    @Test
    void currentTenantIsTheOneOfTheContext() {
        Tenant a = service.createTenant(new CreateTenantCommand("a", "A", null));
        service.createTenant(new CreateTenantCommand("b", "B", null));

        assertThat(TenantContext.callAs(a.id(), service::currentTenant)).isEqualTo(a);
    }

    @Test
    void currentTenantFailsWithoutContextOrWhenUnknown() {
        assertThatThrownBy(service::currentTenant).isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> TenantContext.callAs(UUID.randomUUID(), service::currentTenant))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void listsWithPagination() {
        for (int i = 0; i < 5; i++) {
            service.createTenant(new CreateTenantCommand("t-" + i, "T" + i, null));
        }

        var page = service.list(1, 2);

        assertThat(page.items()).extracting(Tenant::code).containsExactly("t-2", "t-3");
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    private static final class InMemoryTenantRepository implements TenantRepository {

        private final List<Tenant> tenants = new ArrayList<>();

        @Override
        public Optional<Tenant> findById(UUID id) {
            return tenants.stream().filter(t -> t.id().equals(id)).findFirst();
        }

        @Override
        public Tenant create(Tenant tenant) {
            if (tenants.stream().anyMatch(t -> t.code().equals(tenant.code()))) {
                throw new DuplicateTenantCodeException(tenant.code());
            }
            tenants.add(tenant);
            return tenant;
        }

        @Override
        public List<Tenant> findAll(long offset, int limit) {
            return tenants.stream().skip(offset).limit(limit).toList();
        }

        @Override
        public long count() {
            return tenants.size();
        }
    }
}
