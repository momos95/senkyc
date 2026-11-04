package sn.senkyc.api.verification.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senkyc.api.verification.application.VerificationRepository;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.api.verification.domain.Verification;
import sn.senkyc.api.verification.domain.VerificationStatus;

/** Les tables sont soumises à la RLS : la base ne renvoie que les lignes du tenant courant. */
@Repository
class JdbcVerificationRepository implements VerificationRepository {

    private static final String COLUMNS = "id, tenant_id, external_reference, idempotency_key, status, document_type,"
            + " issuing_country, expires_at, created_at, version";

    private static final RowMapper<Verification> MAPPER = JdbcVerificationRepository::map;

    private final JdbcClient jdbc;

    JdbcVerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Verification> findById(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from verification where id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    @Override
    public Optional<Verification> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("select " + COLUMNS + " from verification where idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(MAPPER)
                .optional();
    }

    @Override
    public boolean insert(Verification v) {
        OffsetDateTime created = OffsetDateTime.ofInstant(v.createdAt(), ZoneOffset.UTC);
        return jdbc.sql("insert into verification (id, tenant_id, external_reference, idempotency_key, status,"
                                + " expires_at, document_type, issuing_country, version, created_at, updated_at)"
                                + " values (:id, :tenantId, :reference, :key, :status, :expiresAt, :documentType,"
                                + " :country, :version, :createdAt, :createdAt)"
                                + " on conflict (tenant_id, idempotency_key) where idempotency_key is not null"
                                + " do nothing")
                        .param("id", v.id())
                        .param("tenantId", v.tenantId())
                        .param("reference", v.externalReference())
                        .param("key", v.idempotencyKey())
                        .param("status", v.status().name())
                        .param("expiresAt", OffsetDateTime.ofInstant(v.expiresAt(), ZoneOffset.UTC))
                        .param("documentType", v.documentType().name())
                        .param("country", v.issuingCountry().name())
                        .param("version", v.version())
                        .param("createdAt", created)
                        .update()
                == 1;
    }

    private static Verification map(ResultSet rs, int rowNum) throws SQLException {
        return new Verification(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("external_reference"),
                rs.getString("idempotency_key"),
                VerificationStatus.valueOf(rs.getString("status")),
                DocumentType.valueOf(rs.getString("document_type")),
                IssuingCountry.valueOf(rs.getString("issuing_country").trim()),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }
}
