# ADR 0007 — Stockage objet S3-compatible (MinIO en développement)

- Statut : accepté ; point de vigilance ouvert

## Décision
Les images (CNI, selfies) sont stockées dans un bucket privé S3-compatible, chiffrées côté application
(clé référencée par `encryption_key_id`, semaine 3). Le code n'utilise que l'API S3 : le fournisseur
reste interchangeable.

## Point de vigilance (à vérifier par l'équipe)
La distribution des images Docker de MinIO Community a évolué fin 2025 (à ma connaissance, non
revérifié ici faute d'accès réseau). L'image est donc **épinglée** sur une release datée connue.
Avant toute mise en production, confirmer la disponibilité/licence (AGPLv3) et envisager une
alternative S3 (Garage, SeaweedFS, S3 managé).
