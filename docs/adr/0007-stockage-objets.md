# ADR 0007 — Stockage objet S3-compatible (RustFS en développement)

- Statut : accepté (remplace MinIO) ; à valider à l'usage

## Décision
Les images (CNI, selfies) sont stockées dans un bucket privé S3-compatible, chiffrées côté application
(clé référencée par `encryption_key_id`, semaine 3). Le code n'utilise que l'API S3 : le fournisseur
reste interchangeable (RustFS, Garage, SeaweedFS, S3 managé). Le chiffrement ne dépend pas du serveur.

## Pourquoi pas MinIO
Les images Docker communautaires ont été arrêtées (le tirage de l'image épinglée renvoyait 401 le
3 novembre 2026) et, selon des sources tierces non vérifiées auprès de MinIO, le dépôt est archivé.

## Choix de développement : RustFS
- Licence Apache-2.0 (label de l'image et page du projet), compatible avec la nôtre.
- Image `rustfs/rustfs` tirée avec succès le 3 novembre 2026 ; version 1.0.4 d'après ses labels ;
  épinglée par digest dans `docker-compose.yml`.
- Alternatives évaluées sur sources secondaires : Garage (AGPLv3, compatibilité S3 plus limitée),
  SeaweedFS (plus de composants).

## Réserves
- Projet jeune (1.0.x), certaines fonctions marquées « Preview » ; compatibilité S3 « sous-ensemble testé ».
  Aucune conformité S3 complète n'est supposée : le test d'intégration de l'adaptateur la vérifiera pour
  ce dont SenKYC a besoin.
- Sans identifiants fournis, RustFS démarre avec `rustfsadmin` / `rustfsadmin` : le Compose exige
  `STORAGE_ACCESS_KEY` et `STORAGE_SECRET_KEY` (démarrage bloqué sinon).
- Aucun healthcheck dans l'image ; aucun n'est inventé.
- Le choix pour la production reste ouvert et dépend de l'hébergement.
