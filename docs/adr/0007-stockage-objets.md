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

## Mise en œuvre (semaine 3)
- Port `ObjectStorage` (octets déjà chiffrés) + adaptateur S3 (SDK AWS v2, module S3 seul, accès par chemin).
- `ProtectedObjectStore` : AES-256-GCM, IV aléatoire par objet, **AAD = clé d'objet** (un objet déplacé vers un
  autre tenant ne se déchiffre pas), identifiant de clé écrit dans l'objet (rotation), taille max 10 Mio,
  clé d'objet `tenants/<tenant>/verifications/<verification>/<uuid>` sans donnée personnelle, contrôle du tenant
  à la lecture et à la suppression, échec de déchiffrement = erreur (jamais « valide »).
- Clés dans l'environnement (`STORAGE_ENCRYPTION_KEY_K1`, base64, 32 octets). **Perdre la clé rend les objets
  illisibles.** Un KMS ou un coffre de secrets sera nécessaire en production : l'environnement n'est qu'un
  point de départ.
- Fonction désactivée par défaut (`senkyc.storage.enabled=false`) ; activée, un serveur injoignable ou une clé
  invalide bloque le démarrage.
- Pas encore branché sur un parcours métier : les métadonnées `stored_object` seront écrites avec le flux de
  vérification (semaine 4).
- SDK AWS épinglé en 2.28.x (version non résolue depuis l'environnement de rédaction, à confirmer au build).

## Réserves
- Projet jeune (1.0.x), certaines fonctions marquées « Preview » ; compatibilité S3 « sous-ensemble testé ».
  Aucune conformité S3 complète n'est supposée : le test d'intégration de l'adaptateur la vérifiera pour
  ce dont SenKYC a besoin.
- Sans identifiants fournis, RustFS démarre avec `rustfsadmin` / `rustfsadmin` : le Compose exige
  `STORAGE_ACCESS_KEY` et `STORAGE_SECRET_KEY` (démarrage bloqué sinon).
- Aucun healthcheck dans l'image ; aucun n'est inventé.
- Le choix pour la production reste ouvert et dépend de l'hébergement.
