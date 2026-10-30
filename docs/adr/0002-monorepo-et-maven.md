# ADR 0002 — Monorepo, Maven multi-modules limité

- Statut : accepté (semaine 1)

## Décision
Monorepo `apps/ packages/ infra/ examples/ docs/ tests/` conforme au cahier des charges. Maven
n'agrège que les artefacts Java : `senkyc-registry-spi` et `senkyc-api` (+ `senkyc-java-sdk` en
semaine 10). Le service Python, le widget et la console Angular ont leur propre outillage
(pyproject, npm) et leur propre job CI.

`senkyc-api` reste un module unique (pas un module Maven par domaine) pour éviter une
complexité de build sans bénéfice de déploiement.

## Conséquences
`mvn verify` à la racine construit et teste tout le Java. Le contexte Docker de l'API est la racine
du dépôt (le reactor est nécessaire pour résoudre `-am`).
