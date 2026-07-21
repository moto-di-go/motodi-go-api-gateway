# Tickets - motodi-go-devops

## Labels communs

`repo:motodi-go-devops`, `project:moto-di-go`

# DEVOPS-FOUND-001 - Initialisation organisation, depots et protections GitHub

## Priorite

HIGH

## Delai estime

3d

## Dependances

- Aucune

## Objectif

En tant qu'equipe technique, je veux disposer de depots GitHub, branches protegees et conventions communes afin de demarrer le developpement sur une base controlable.

## Taches

- Creer/configurer les depots `motodi-go-backend-core`, `motodi-go-backend-ai`, `motodi-go-api-gateway`, `motodi-go-mobile`, `motodi-go-admin-dashboard` et `motodi-go-devops`.
- Definir branches `main`, `dev`, regles PR, CODEOWNERS et templates d'issues.
- Ajouter conventions de nommage, labels GitHub et milestones MVP/V1.1/V1.2.
- Documenter la strategie d'environnements local, staging et production.

## Criteres d'acceptation

- Chaque depot a des branches protegees et au moins un template d'issue.
- Les PR exigent revue et checks avant merge.
- Les secrets ne sont pas stockes dans le code.
- Les tickets de ce dossier peuvent etre importes ou copies dans les depots cibles.

## References

- Backlog: MGO-001
- Cahier des charges: section 5.3, section 7

# DEVOPS-ENV-001 - Stack locale Docker avec PostgreSQL/PostGIS et services socle

## Priorite

HIGH

## Delai estime

3d

## Dependances

- DEVOPS-FOUND-001

## Objectif

En tant que developpeur, je veux lancer l'environnement local complet afin de tester les services Spring Boot, FastAPI, API Gateway, PostGIS et back-office sans configuration manuelle.

## Taches

- Creer `docker-compose` pour PostgreSQL/PostGIS, backend core, gateway, backend AI et dashboard.
- Ajouter healthchecks, volumes de developpement et reseau interne.
- Fournir fichiers `.env.example` par service.
- Documenter les commandes de demarrage, migration et reset local.

## Criteres d'acceptation

- `docker compose up` lance les services socle localement.
- PostgreSQL expose l'extension PostGIS.
- Les healthchecks distinguent service pret et service demarre.
- Les variables sensibles sont referencees sans valeur reelle.

## References

- Backlog: MGO-001, MGO-002, MGO-003
- Cahier des charges: section 5.1, section 5.3

# DEVOPS-CI-001 - Pipelines CI multi-depots

## Priorite

HIGH

## Delai estime

3d

## Dependances

- DEVOPS-FOUND-001
- DEVOPS-ENV-001

## Objectif

En tant qu'equipe technique, je veux que chaque depot execute automatiquement build, tests et controles de qualite afin de securiser les livraisons.

## Taches

- Ajouter GitHub Actions pour Java/Spring, Flutter, Next.js, FastAPI et gateway.
- Publier les rapports de tests et de lint.
- Ajouter scan de secrets et controle de dependances.
- Definir les checks obligatoires sur PR.

## Criteres d'acceptation

- Chaque depot a une pipeline CI verte sur un commit minimal.
- Une PR qui casse tests ou lint bloque le merge.
- Les secrets accidentels sont detectes.
- Les temps de pipeline restent compatibles avec le rythme de sprint.

## References

- Backlog: MGO-001, MGO-034
- Cahier des charges: section 5.3, section 7

# DEVOPS-RELEASE-001 - Deploiement staging/production et plan de rollback

## Priorite

HIGH

## Delai estime

5d

## Dependances

- DEVOPS-CI-001
- GATE-PLAT-001
- CORE-DATA-001
- ADMIN-AUTH-001

## Objectif

En tant qu'equipe produit, je veux deployer le MVP sur VPS Ubuntu avec rollback afin de rendre la solution exploitable en production.

## Taches

- Creer pipelines de livraison staging et production.
- Configurer TLS, reverse proxy, variables d'environnement et sauvegardes PostGIS.
- Ajouter smoke tests post-deploiement pour API, gateway, dashboard et IA.
- Documenter rollback, restauration base et procedure incident.

## Criteres d'acceptation

- Un tag de release declenche un deploiement reproductible.
- Le deploiement verifie `/health` sur les services.
- Un rollback documente permet de revenir a la version precedente.
- Les sauvegardes base et fichiers sensibles sont testees.

## References

- Backlog: MGO-034
- Cahier des charges: section 6, section 7
