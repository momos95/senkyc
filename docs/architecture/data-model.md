# Modèle de données initial

Aucune donnée personnelle extraite de la CNI n'est stockée à ce stade. Les champs OCR seront ajoutés
(chiffrés au niveau applicatif) en semaine 4. Les images ne sont **jamais** en base : seules des
métadonnées pointent vers l'objet chiffré dans MinIO.

```mermaid
erDiagram
    tenant ||--o{ verification : possede
    verification ||--o{ stored_object : "(tenant_id, verification_id)"
    tenant ||..o{ audit_event : "tenant_id (sans FK)"

    tenant {
        uuid id PK
        varchar code UK
        varchar name
        varchar status "ACTIVE|SUSPENDED"
        int retention_days
    }
    verification {
        uuid id PK
        uuid tenant_id FK
        varchar external_reference
        varchar idempotency_key "unique par tenant"
        varchar status "9 statuts du workflow"
        timestamptz expires_at
        bigint version "verrou optimiste"
    }
    stored_object {
        uuid id PK
        uuid tenant_id
        uuid verification_id
        varchar kind "DOCUMENT_FRONT|DOCUMENT_BACK|SELFIE"
        varchar bucket
        varchar object_key
        char sha256
        varchar encryption_key_id
        timestamptz delete_after
        timestamptz deleted_at
    }
    audit_event {
        bigint id PK
        uuid tenant_id
        varchar actor
        varchar action
        varchar correlation_id
        jsonb details
    }
```

Choix notables :

- **Isolation** : `tenant_id` partout ; clé étrangère composite `(tenant_id, verification_id)` qui
  interdit à l'échelle de la base de rattacher un objet à la vérification d'un autre tenant (testé
  dans `SenkycApiApplicationIT`). Row-Level Security PostgreSQL : semaine 2.
- **Statuts** : contrainte `CHECK` alignée sur les 9 statuts du cahier des charges.
- **Idempotence** : index unique partiel `(tenant_id, idempotency_key)`.
- **Rétention** : `stored_object.delete_after` + index partiel pour le job de purge ;
  `tenant.retention_days` vaut 30 par défaut **à titre provisoire** (à valider juridiquement).
- **Audit** : pas de FK vers `tenant`, pour survivre à sa suppression. L'append-only sera appliqué
  par les privilèges du rôle applicatif (INSERT seul) en semaine 2.
- **Rôles BD** : en semaine 1 l'application utilise le rôle propriétaire ; un rôle applicatif
  sans droits DDL sera créé en semaine 2 avec la RLS.
