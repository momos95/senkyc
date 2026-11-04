# ADR 0010 — Périmètre produit et éditions

- Statut : accepté (décision du porteur du produit, 4 novembre 2026)

## Périmètre
- **Documents** : carte nationale d'identité (CNI), passeport, permis de conduire. Aucun autre document.
- **Pays** : Sénégal d'abord, puis les États de l'UEMOA (BJ, BF, CI, GW, ML, NE, SN, TG).
  Le schéma accepte les 8 pays (`verification.issuing_country`) ; l'énumération `IssuingCountry` n'active que
  `SN`. Ouvrir un pays suppose d'avoir écrit et testé ses règles de documents : aucune règle n'est
  supposée identique d'un pays à l'autre.

## Éditions
- **Gratuite** : contrôles techniques (cohérence, OCR, face matching, liveness). **Sans** contrôle d'intégrité
  auprès des services officiels (connecteurs). Le meilleur résultat est `APPROVED_TECHNICAL`.
- **Avec contrôle officiel** : vérification par des connecteurs vers des services réellement autorisés ;
  seule voie vers `VERIFIED_OFFICIAL`.

## Mécanisme retenu : droit par tenant, même code
Tout le code reste sous Apache-2.0. `tenant.official_verification_enabled` (faux par défaut) active la
vérification officielle ; le moteur de décision (semaines 5+) devra refuser `VERIFIED_OFFICIAL` et ne jamais
appeler de connecteur lorsque le drapeau est faux. Aucun connecteur n'existe encore.

## Limites assumées
- Ce drapeau est un **droit contractuel**, pas une protection technique : un auto-hébergeur peut le modifier
  dans sa propre base. La séparation technique (open core, connecteurs en dépôt non public) a été écartée pour
  l'instant ; elle reste possible plus tard sans changer le SPI.
- Il n'existe pas encore d'endpoint pour accorder ce droit (à ajouter avec le moteur de décision).
- Les autorisations d'accès aux services officiels et la conformité de chaque pays relèvent d'un avis
  juridique, non couvert par ce dépôt.
