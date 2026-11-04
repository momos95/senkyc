package sn.senkyc.api.verification.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.senkyc.api.verification.application.CreateVerificationCommand;
import sn.senkyc.api.verification.application.VerificationService;
import sn.senkyc.api.verification.application.VerificationService.CreateResult;
import sn.senkyc.api.verification.domain.DocumentPart;
import sn.senkyc.api.verification.domain.StoredDocument;

@RestController
@RequestMapping("/api/v1/verifications")
class VerificationController {

    private final VerificationService service;

    VerificationController(VerificationService service) {
        this.service = service;
    }

    /** 201 à la création ; 200 si la même clé d'idempotence rejoue la même demande. */
    @PostMapping
    ResponseEntity<VerificationResponse> create(
            @Valid @RequestBody CreateVerificationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    @Pattern(regexp = "^[A-Za-z0-9_-]{8,128}$", message = "must be 8 to 128 characters: letters, digits, - or _")
                    String idempotencyKey) {
        CreateResult result = service.create(new CreateVerificationCommand(
                request.documentType(), request.issuingCountry(), request.externalReference(), idempotencyKey));
        VerificationResponse body = VerificationResponse.from(result.verification(), java.util.List.of());
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/verifications/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    VerificationResponse get(@PathVariable UUID id) {
        return VerificationResponse.from(service.get(id));
    }

    /** Envoi d'une image (multipart) : le format, le poids et les dimensions sont contrôlés avant tout stockage. */
    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<VerificationResponse.DocumentResponse> upload(
            @PathVariable UUID id,
            @RequestParam("part") DocumentPart part,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        StoredDocument stored = service.uploadDocument(id, part, file.getBytes(), file.getContentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(VerificationResponse.DocumentResponse.from(stored));
    }
}
