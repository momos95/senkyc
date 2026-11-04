package sn.senkyc.api.verification.application;

import java.util.Optional;
import java.util.UUID;
import sn.senkyc.api.verification.domain.Verification;

/** Port sortant. Toutes les lectures sont filtrées par tenant (RLS) : un id d'un autre tenant est « introuvable ». */
public interface VerificationRepository {

    Optional<Verification> findById(UUID id);

    Optional<Verification> findByIdempotencyKey(String idempotencyKey);

    /** @return faux si la clé d'idempotence existait déjà (rien n'est écrit, sans erreur de transaction) */
    boolean insert(Verification verification);
}
