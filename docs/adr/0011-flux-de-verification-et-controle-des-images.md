# ADR 0011 — Flux de vérification et contrôle des images soumises

- Statut : implémenté en semaine 3 ; tests d'intégration à exécuter par un humain (Docker requis)

## Flux
1. `POST /api/v1/verifications` (permission `kyc:verifications:write`) : crée une demande `PENDING`
   (`documentType`, `issuingCountry`, `externalReference` optionnelle). En-tête optionnel `Idempotency-Key`
   (8-128 caractères `A-Za-z0-9_-`) : même clé et même demande → `200` avec la même ressource
   (`Idempotent-Replay: true`) ; même clé et demande différente → `409`.
2. `POST /api/v1/verifications/{id}/documents` (multipart : `part` = `DOCUMENT_FRONT` | `DOCUMENT_BACK` |
   `SELFIE`, `file`) : une seule image par partie ; le passeport n'a pas de verso.
3. `GET /api/v1/verifications/{id}` (permission `kyc:verifications:read`) : statut et métadonnées des
   documents, jamais d'image, de clé d'objet ni de donnée d'identité.

Refus : pays non activé (`422`), tenant suspendu (`403`), vérification expirée ou non `PENDING` (`409`),
stockage désactivé ou en panne (`503`, jamais un succès silencieux). Un identifiant d'un autre tenant est
`404` (RLS). La durée de vie d'une demande est de 24 h (`senkyc.verification.ttl`).

## Contrôle des images (avant tout stockage)
- **Formats** : JPEG et PNG uniquement, reconnus par leur contenu (octets magiques), pas par le nom ni l'en-tête.
  Un type annoncé qui contredit le contenu est refusé ; absent ou `application/octet-stream`, on se fie au contenu.
- **Poids** : 20 Kio à 8 Mio. **Dimensions** : plus petit côté ≥ 480 px, au plus 40 millions de pixels,
  rapport plus grand/plus petit côté ≤ 4. **Structure** : fin de fichier intacte (JPEG `FFD9`, PNG `IEND`).
- Seul l'en-tête est lu pour les dimensions : l'image n'est jamais décodée entièrement (pas d'épuisement mémoire
  par une image « bombe »). La limite multipart du serveur (8 Mo) s'applique en amont.
- Les valeurs sont des **valeurs de départ pour une photo de smartphone ordinaire**, réglables
  (`senkyc.verification.documents.*`) ; elles n'ont pas été calibrées sur de vraies images de documents.
- Réponse en cas de refus : `422` avec un code (`reason`) sans contenu de l'image.
- Le nom de fichier fourni par le client n'est jamais utilisé.

## Ce que ce contrôle ne fait pas
Pas d'antivirus, pas de détection de falsification ou de document photographié sur écran, pas de ré-encodage
(les métadonnées EXIF d'origine sont conservées dans l'objet chiffré), pas de limitation de débit. WebP/HEIC ne
sont pas acceptés : une application mobile doit convertir en JPEG ou PNG.

## Données et audit
Chaque image est chiffrée (ADR 0007) puis référencée dans `stored_object`, avec une date de purge calculée depuis
la rétention du tenant (valeur provisoire de 30 jours, à valider juridiquement). La purge elle-même n'est pas encore
implémentée. La création et chaque dépôt écrivent un événement d'audit (auteur, tenant, partie, type, taille) sans
empreinte ni contenu.
