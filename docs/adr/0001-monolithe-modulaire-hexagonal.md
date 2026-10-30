# ADR 0001 — Monolithe modulaire, architecture hexagonale pragmatique

- Statut : accepté (semaine 1)

## Contexte
Plateforme auto-hébergeable par des développeurs tiers ; équipe réduite ; 10 domaines métier.

## Décision
Un seul déploiement Java (monolithe modulaire), packages par domaine, ports/adaptateurs là où
une frontière externe existe (IA, stockage, registre, persistance). Pas de microservice
supplémentaire : le service Python est justifié par l'écosystème IA (OpenCV, ONNX) et son cycle
de vie/ressources (CPU/GPU) différents.

## Conséquences
- Déploiement et exploitation simples ; transactions locales.
- Les frontières entre modules reposent sur la discipline et sur des tests ArchUnit (à ajouter dès deux modules).
- Extraction ultérieure possible en service si un domaine l'exige.
