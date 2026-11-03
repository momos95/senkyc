package sn.senkyc.api.tenant.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senkyc.api.tenant.application.TenantRepository;
import sn.senkyc.api.tenant.domain.DuplicateTenantCodeException;
import sn.senkyc.api.tenant.domain.Tenant;
import sn.senkyc.api.tenant.domain.TenantStatus;

/** Adaptateur JDBC : la table {@code tenant} est simple et sans RLS ; JPA arrive avec les agrégats de la semaine 3. */
@Repository
class JdbcTenantRepository implements TenantRepository {

    private static final String COLUMNS = "id, code, name, status, retention_days, created_at";

    private static final RowMapper<Tenant> MAPPER = JdbcTenantRepository::map;

    private final JdbcClient jdbc;

    JdbcTenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from tenant where id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    @Override
    public Tenant create(Tenant tenant) {
        try {
            jdbc.sql("insert into tenant (id, code, name, status, retention_days, created_at)"
                            + " values (:id, :code, :name, :status, :retention, :createdAt)")
                    .param("id", tenant.id())
                    .param("code", tenant.code())
                    .param("name", tenant.name())
                    .param("status", tenant.status().name())
                    .param("retention", tenant.retentionDays())
                    .param("createdAt", OffsetDateTime.ofInstant(tenant.createdAt(), ZoneOffset.UTC))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateTenantCodeException(tenant.code());
        }
        return tenant;
    }

    @Override
    public List<Tenant> findAll(long offset, int limit) {
        return jdbc.sql("select " + COLUMNS + " from tenant order by created_at, id limit :limit offset :offset")
                .param("limit", limit)
                .param("offset", offset)
                .query(MAPPER)
                .list();
    }

    @Override
    public long count() {
        return jdbc.sql("select count(*) from tenant").query(Long.class).single();
    }

    private static Tenant map(ResultSet rs, int rowNum) throws SQLException {
        return new Tenant(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getInt("retention_days"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
