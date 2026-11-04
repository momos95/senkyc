package sn.senkyc.api.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import sn.senkyc.api.platform.tenant.TenantContext;
import sn.senkyc.api.platform.web.CorrelationIdFilter;

@Repository
class JdbcAuditLog implements AuditLog {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    JdbcAuditLog(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void record(String action, String resourceType, String resourceId, Map<String, Object> details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null && authentication.getName() != null ? authentication.getName() : "system";
        jdbc.sql("insert into audit_event (tenant_id, actor, action, resource_type, resource_id, correlation_id, details)"
                        + " values (:tenant, :actor, :action, :resourceType, :resourceId, :correlationId,"
                        + " cast(:details as jsonb))")
                .param("tenant", TenantContext.current().orElse(null))
                .param("actor", actor)
                .param("action", action)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY))
                .param("details", toJson(details))
                .update();
    }

    private String toJson(Map<String, Object> details) {
        try {
            return mapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details are not serializable", e);
        }
    }
}
