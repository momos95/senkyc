package sn.senkyc.api.verification.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sn.senkyc.api.platform.security.SecurityConfig;
import sn.senkyc.api.storage.application.StorageException;
import sn.senkyc.api.verification.application.InvalidImageException;
import sn.senkyc.api.verification.application.InvalidImageException.Reason;
import sn.senkyc.api.verification.application.StorageNotConfiguredException;
import sn.senkyc.api.verification.application.VerificationService;
import sn.senkyc.api.verification.application.VerificationService.CreateResult;
import sn.senkyc.api.verification.application.VerificationService.VerificationView;
import sn.senkyc.api.verification.domain.DocumentPart;
import sn.senkyc.api.verification.domain.DocumentType;
import sn.senkyc.api.verification.domain.IdempotencyConflictException;
import sn.senkyc.api.verification.domain.IssuingCountry;
import sn.senkyc.api.verification.domain.StoredDocument;
import sn.senkyc.api.verification.domain.Verification;
import sn.senkyc.api.verification.domain.VerificationNotFoundException;
import sn.senkyc.api.verification.domain.VerificationStatus;

@WebMvcTest(VerificationController.class)
@Import(SecurityConfig.class)
class VerificationControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final Verification VERIFICATION = new Verification(
            ID, TENANT, "ref-1", null, VerificationStatus.PENDING, DocumentType.NATIONAL_ID, IssuingCountry.SN,
            NOW.plusSeconds(86_400), NOW, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private VerificationService service;

    private static RequestPostProcessor writer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_kyc:verifications:write"));
    }

    private static RequestPostProcessor reader() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_kyc:verifications:read"));
    }

    private static final String BODY = "{\"documentType\":\"NATIONAL_ID\",\"issuingCountry\":\"SN\",\"externalReference\":\"ref-1\"}";

    @Test
    void createReturns201WithLocationAndNoSensitiveData() throws Exception {
        when(service.create(any())).thenReturn(new CreateResult(VERIFICATION, false));

        mockMvc.perform(post("/api/v1/verifications").with(writer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/verifications/" + ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.documentType").value("NATIONAL_ID"))
                .andExpect(jsonPath("$.documents").isEmpty())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    void replayedCreationReturns200() throws Exception {
        when(service.create(any())).thenReturn(new CreateResult(VERIFICATION, true));

        mockMvc.perform(post("/api/v1/verifications")
                        .with(writer())
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
    }

    @Test
    void invalidRequestsAreRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/verifications").with(writer()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/verifications")
                        .with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"VOTER_CARD\",\"issuingCountry\":\"SN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/verifications")
                        .with(writer())
                        .header("Idempotency-Key", "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        when(service.create(any())).thenThrow(new IdempotencyConflictException());

        mockMvc.perform(post("/api/v1/verifications")
                        .with(writer())
                        .header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void getReturnsTheVerificationAndItsDocumentsMetadataOnly() throws Exception {
        StoredDocument doc = new StoredDocument(
                UUID.randomUUID(), TENANT, ID, DocumentPart.DOCUMENT_FRONT, "bucket", "tenants/x/secret-key",
                "image/png", 1234, "a".repeat(64), "k1", NOW, NOW.plusSeconds(60));
        when(service.get(ID)).thenReturn(new VerificationView(VERIFICATION, List.of(doc)));

        mockMvc.perform(get("/api/v1/verifications/{id}", ID).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].part").value("DOCUMENT_FRONT"))
                .andExpect(jsonPath("$.documents[0].sizeBytes").value(1234))
                .andExpect(jsonPath("$.documents[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.documents[0].sha256").doesNotExist());
    }

    @Test
    void unknownVerificationReturns404() throws Exception {
        when(service.get(ID)).thenThrow(new VerificationNotFoundException());

        mockMvc.perform(get("/api/v1/verifications/{id}", ID).with(reader())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/verifications/not-a-uuid").with(reader())).andExpect(status().isBadRequest());
    }

    @Test
    void readersCannotCreateAndAnonymousCallersAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/verifications").with(reader()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/verifications").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    // --- Envoi de documents -----------------------------------------------------------------------

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "anything.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    void uploadReturns201WithMetadataOnly() throws Exception {
        StoredDocument doc = new StoredDocument(
                UUID.randomUUID(), TENANT, ID, DocumentPart.SELFIE, "bucket", "tenants/x/k", "image/png", 3,
                "b".repeat(64), "k1", NOW, NOW.plusSeconds(60));
        when(service.uploadDocument(eq(ID), eq(DocumentPart.SELFIE), any(), eq("image/png"))).thenReturn(doc);

        mockMvc.perform(multipart("/api/v1/verifications/{id}/documents", ID)
                        .file(file())
                        .param("part", "SELFIE")
                        .with(writer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.part").value("SELFIE"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.objectKey").doesNotExist());
    }

    @Test
    void invalidImageReturns422WithAReasonCode() throws Exception {
        when(service.uploadDocument(any(), any(), any(), any())).thenThrow(new InvalidImageException(Reason.TOO_SMALL));

        mockMvc.perform(multipart("/api/v1/verifications/{id}/documents", ID)
                        .file(file())
                        .param("part", "SELFIE")
                        .with(writer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("TOO_SMALL"));
    }

    @Test
    void storageProblemsReturn503() throws Exception {
        when(service.uploadDocument(any(), any(), any(), any())).thenThrow(new StorageNotConfiguredException());
        mockMvc.perform(multipart("/api/v1/verifications/{id}/documents", ID).file(file()).param("part", "SELFIE").with(writer()))
                .andExpect(status().isServiceUnavailable());

        when(service.uploadDocument(any(), any(), any(), any())).thenThrow(new StorageException("down"));
        mockMvc.perform(multipart("/api/v1/verifications/{id}/documents", ID).file(file()).param("part", "SELFIE").with(writer()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownPartAndMissingFileAreRejectedWith400() throws Exception {
        mockMvc.perform(multipart("/api/v1/verifications/{id}/documents", ID).file(file()).param("part", "PASSPORT_PHOTO").with(writer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/verifications/{id}/documents", ID).param("part", "SELFIE").with(writer()))
                .andExpect(status().isBadRequest());
    }
}
