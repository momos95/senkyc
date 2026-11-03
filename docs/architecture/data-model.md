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
  dans `SenkycApiApplicationIT`). Row-Level Security PostgreSQL (migration `002-tenant-isolation.sql`, voir ADR 0009) : `ENABLE` + `FORCE` sur `verification`, `stored_object`, `audit_event`. La table `tenant` n'a pas de RLS (la lecture de son propre tenant est filtrée par l'application).
- **Statuts** : contrainte `CHECK` alignée sur les 9 statuts du cahier des charges.
- **Idempotence** : index unique partiel `(tenant_id, idempotency_key)`.
- **Rétention** : `stored_object.delete_after` + index partiel pour le job de purge ;
  `tenant.retention_days` vaut 30 par défaut **à titre provisoire** (à valider juridiquement).
- **Audit** : pas de FK vers `tenant`, pour survivre à sa suppression. L'append-only est appliqué
  par les privilèges du rôle applicatif (SELECT/INSERT seuls).
- **Rôles BD** : Liquibase s'exécute avec le rôle propriétaire ; l'application utilise `senkyc_app`
  (sans DDL, soumis à la RLS).
