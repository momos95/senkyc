# ADR 0003 — Service IA : HTTP/JSON synchrone interne, jeton de service, sans état

- Statut : accepté (semaine 1)

## Décision
- Appels synchrones HTTP/JSON de l'API vers le service FastAPI (latence d'une vérification
  interactive ; pas de file de messages tant qu'aucun traitement asynchrone n'est requis —
  RabbitMQ reste une option si le face matching/liveness doit être mis en file).
- Authentification par jeton Bearer partagé, comparaison en temps constant, fail-closed (503 si non
  configuré). Le réseau Compose/Kubernetes isole le service ; un durcissement mTLS pourra suivre.
- Le service IA est **sans état** et reçoit les images en octets depuis l'API : il ne reçoit jamais
  d'URL à télécharger (SSRF) ni d'identifiants de stockage, ne persiste et ne journalise aucune image.
- Documentation OpenAPI/Swagger désactivée sur le service interne.

## Alternatives écartées
gRPC (outillage plus lourd pour le gain actuel) ; URL présignées S3 (élargit la surface SSRF et les droits).

## Conséquences
La résilience (timeouts, circuit breaker éventuel, statut `MANUAL_REVIEW`/`FAILED` en cas
d'indisponibilité) est traitée dans le workflow (semaines 3 et 8).
