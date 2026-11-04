# ADR 0010 — Périmètre produit et éditions

- Statut : accepté (décision du porteur du produit, 4 novembre 2026)

## Périmètre
- **Documents** : carte nationale d'identité (CNI), passeport, permis de conduire. Aucun autre document.
- **Pays** : Sénégal d'abord, puis les États de l'UEMOA (BJ, BF, CI, GW, ML, NE, SN, TG).
  Le schéma accepte les 8 pays (`verification.issuing_country`) ; un pays n'est actif que s'il est configuré
  (section suivante). Ouvrir un pays suppose d'avoir écrit et testé ses règles de documents : aucune règle n'est
  supposée identique d'un pays à l'autre.

## Configuration par pays
Un pays est actif s'il figure dans `senkyc.countries` avec `enabled: true`. Pour chaque pays actif, on indique,
par type de document, le connecteur de vérification d'intégrité :

```yaml
senkyc:
  countries:
    SN:
      enabled: true
      documents:
        NATIONAL_ID: { connector: <identifiant-du-connecteur> }   # absent = KYC commun seulement
```

- Type de document sans connecteur → **pas de contrôle d'intégrité** : seules les fonctions communes
  du KYC s'appliquent (résultat maximal `APPROVED_TECHNICAL`).
- Pays absent ou `enabled: false` → vérifications refusées pour ce pays.
- Au démarrage, la configuration est validée (fail-fast) : pays ou document inconnu, connecteur introuvable,
  identifiant en double, connecteur qui ne déclare pas gérer le pays → l'API ne démarre pas.
- Un connecteur n'est appelé que si le tenant y a droit (`RegistryRouter`). Les paramètres propres à un
  connecteur (adresse du service, identifiants) appartiennent à ce connecteur et viennent de l'environnement.
- État actuel : aucun connecteur n'est fourni ; `SN` est activé sans document. Le routage est écrit et testé,
  mais aucun appel de connecteur n'est encore fait par un parcours métier.
- Clé de document en variable d'environnement : les noms contenant `_` (NATIONAL_ID) s'écrivent mal ainsi ;
  utiliser un fichier de configuration (par exemple `SPRING_CONFIG_IMPORT`).

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
