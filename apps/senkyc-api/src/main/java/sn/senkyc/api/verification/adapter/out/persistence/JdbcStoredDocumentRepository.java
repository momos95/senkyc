package sn.senkyc.api.verification.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import sn.senkyc.api.verification.application.StoredDocumentRepository;
import sn.senkyc.api.verification.domain.DocumentPart;
import sn.senkyc.api.verification.domain.StoredDocument;

@Repository
class JdbcStoredDocumentRepository implements StoredDocumentRepository {

    private static final RowMapper<StoredDocument> MAPPER = JdbcStoredDocumentRepository::map;

    private final JdbcClient jdbc;

    JdbcStoredDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StoredDocument> findByVerification(UUID verificationId) {
        return jdbc.sql("select id, tenant_id, verification_id, kind, bucket, object_key, content_type, size_bytes,"
                        + " sha256, encryption_key_id, created_at, delete_after from stored_object"
                        + " where verification_id = :id and deleted_at is null order by created_at, id")
                .param("id", verificationId)
                .query(MAPPER)
                .list();
    }

    @Override
    public boolean insert(StoredDocument d) {
        return jdbc.sql("insert into stored_object (id, tenant_id, verification_id, kind, bucket, object_key,"
                                + " content_type, size_bytes, sha256, encryption_key_id, created_at, delete_after)"
                                + " values (:id, :tenantId, :verificationId, :kind, :bucket, :objectKey,"
                                + " :contentType, :sizeBytes, :sha256, :keyId, :createdAt, :deleteAfter)"
                                + " on conflict (verification_id, kind) where deleted_at is null do nothing")
                        .param("id", d.id())
                        .param("tenantId", d.tenantId())
                        .param("verificationId", d.verificationId())
                        .param("kind", d.part().name())
                        .param("bucket", d.bucket())
                        .param("objectKey", d.objectKey())
                        .param("contentType", d.contentType())
                        .param("sizeBytes", d.sizeBytes())
                        .param("sha256", d.sha256())
                        .param("keyId", d.encryptionKeyId())
                        .param("createdAt", OffsetDateTime.ofInstant(d.createdAt(), ZoneOffset.UTC))
                        .param("deleteAfter", OffsetDateTime.ofInstant(d.deleteAfter(), ZoneOffset.UTC))
                        .update()
                == 1;
    }

    private static StoredDocument map(ResultSet rs, int rowNum) throws SQLException {
        return new StoredDocument(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("verification_id", UUID.class),
                DocumentPart.valueOf(rs.getString("kind")),
                rs.getString("bucket"),
                rs.getString("object_key"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256").trim(),
                rs.getString("encryption_key_id"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("delete_after", OffsetDateTime.class).toInstant());
    }
}
