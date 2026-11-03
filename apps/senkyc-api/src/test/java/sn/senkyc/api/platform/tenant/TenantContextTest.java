package sn.senkyc.api.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void isEmptyByDefaultAndRequireFails() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(TenantContextMissingException.class);
    }

    @Test
    void callAsSetsAndRestoresThePreviousTenant() {
        UUID inside = TenantContext.callAs(a, () -> {
            assertThat(TenantContext.callAs(b, TenantContext::require)).isEqualTo(b);
            return TenantContext.require();
        });

        assertThat(inside).isEqualTo(a);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void callAsRestoresEvenWhenTheActionFails() {
        assertThatThrownBy(() -> TenantContext.runAs(a, () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.current()).isEmpty();
    }
}
