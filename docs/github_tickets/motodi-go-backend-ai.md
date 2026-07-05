# Tickets - motodi-go-backend-ai

## Labels communs

`repo:motodi-go-backend-ai`, `project:moto-di-go`

# AI-MATCH-001 - Assistant IA de recommandation de covoiturage

## Priorite

MEDIUM

## Delai estime

5d

## Dependances

- CORE-INTER-001
- CORE-BOOK-001
- GATE-INTEGRATIONS-001

## Objectif

En tant que voyageur, je veux etre assiste par un agent IA de covoiturage afin d'identifier les trajets ou regroupements les plus pertinents.

## Taches

- Creer microservice FastAPI avec endpoint de recommandation.
- Prendre en compte ville, quartier, horaire, places restantes, historique et contraintes voyageur.
- Retourner une explication courte de chaque recommandation.
- Fournir fallback deterministic si le modele ou les donnees sont insuffisants.

## Criteres d'acceptation

- Les suggestions s'appuient sur les annonces existantes.
- Chaque suggestion inclut score, raison lisible et identifiant d'annonce.
- Le service repond avec un fallback si les donnees sont vides.
- Les logs ne contiennent pas de donnees personnelles brutes.

## References

- Backlog: MGO-018
- Cahier des charges: F-05, section 5.3
- BMC: assistant IA comme levier de remplissage interurbain

# AI-METRICS-001 - Mesure d'utilisation et qualite de l'assistant IA

## Priorite

MEDIUM

## Delai estime

2d

## Dependances

- AI-MATCH-001
- CORE-ANALYTICS-001

## Objectif

En tant que decideur, je veux mesurer l'usage de l'assistant IA afin d'evaluer son impact sur le remplissage et l'adoption.

## Taches

- Emettre evenements anonymises d'appel, latence, resultat et clic.
- Calculer taux d'utilisation, taux de suggestion acceptee et latence moyenne.
- Exposer un endpoint metriques pour l'admin dashboard.
- Ajouter seuils d'alerte en cas d'erreurs ou latence elevee.

## Criteres d'acceptation

- Les metriques sont agregees sans exposer l'identite voyageur.
- Les KPI IA sont consultables par periode.
- Les erreurs IA sont suivies distinctement des erreurs gateway.
- Les tests couvrent anonymisation et calculs de base.

## References

- Backlog: MGO-018, MGO-033
- Cahier des charges: section 8.2

