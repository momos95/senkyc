package sn.senkyc.api.platform.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant courant du thread. Alimenté par {@link TenantContextFilter} depuis le jeton validé, ou
 * explicitement par {@link #callAs}/{@link #runAs} pour les traitements système (purge, jobs).
 * Absent => la base ne renvoie et n'accepte aucune ligne protégée (fail-closed).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** @throws TenantContextMissingException si aucun tenant n'est établi */
    public static UUID require() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new TenantContextMissingException();
        }
        return tenantId;
    }

    static void set(UUID tenantId) {
        CURRENT.set(Objects.requireNonNull(tenantId, "tenantId"));
    }

    static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    private static void restore(UUID previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
