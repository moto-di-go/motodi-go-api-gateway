# Tickets - motodi-go-api-gateway

## Labels communs

`repo:motodi-go-api-gateway`, `project:moto-di-go`

# GATE-PLAT-001 - API Gateway securisee et endpoints versionnes

## Priorite

HIGH

## Delai estime

3d

## Dependances

- DEVOPS-ENV-001
- CORE-DATA-001

## Objectif

En tant que client mobile ou web, je veux acceder a une API versionnee et documentee afin d'utiliser les services MOTO'DI GO de facon fiable.

## Taches

- Configurer Spring Cloud Gateway avec routes `/api/v1`.
- Ajouter endpoints de health, readiness et documentation OpenAPI agregee.
- Centraliser CORS, headers securite et correlation id.
- Documenter la convention de routage par service.

## Criteres d'acceptation

- Les routes publiques et privees sont exposees sous `/api/v1`.
- La documentation API est accessible en environnement de test.
- Les reponses incluent un identifiant de correlation.
- Les endpoints courants respectent la cible de latence inferieure a 200 ms hors dependance externe.

## References

- Backlog: MGO-003
- Cahier des charges: section 5.1, section 6.1

# GATE-AUTH-001 - Validation JWT, RBAC et rate limiting

## Priorite

HIGH

## Delai estime

3d

## Dependances

- GATE-PLAT-001
- CORE-AUTH-001
- CORE-PROF-001

## Objectif

En tant que plateforme, je veux filtrer les acces par jeton et role afin de proteger les parcours passager, mototaxi, transporteur, agence et administrateur.

## Taches

- Verifier JWT sur les routes privees.
- Appliquer les regles RBAC par route et methode HTTP.
- Ajouter rate limiting sur authentification, OTP, paiements et callbacks.
- Journaliser les refus d'acces et tentatives suspectes.

## Criteres d'acceptation

- Une requete sans JWT valide est rejetee sur endpoint prive.
- Les roles non autorises recoivent une erreur 403 stable.
- Les tentatives abusives sont limitees et journalisees.
- Les erreurs sont localisables et sans fuite d'information sensible.

## References

- Backlog: MGO-003, MGO-005, MGO-006
- Cahier des charges: section 6.2

# GATE-REALTIME-001 - Routage temps reel GPS, suivi et notifications

## Priorite

HIGH

## Delai estime

3d

## Dependances

- GATE-AUTH-001
- CORE-GEO-001
- CORE-TRACK-001
- CORE-NOTIF-001

## Objectif

En tant qu'utilisateur, je veux recevoir les changements de position, reservation et statut de trajet afin de suivre les operations en temps reel.

## Taches

- Exposer les routes WebSocket, SSE ou polling long selon l'architecture retenue.
- Propager authentification et correlation id vers backend core.
- Ajouter limites de frequence pour les positions GPS.
- Prevoir fallback polling pour reseaux instables.

## Criteres d'acceptation

- Un conducteur valide peut transmettre sa position sans exposer les autres utilisateurs.
- Un passager recoit les changements de statut de son trajet actif.
- Les connexions degradees peuvent basculer en polling.
- Les flux temps reel sont couverts par tests d'integration.

## References

- Backlog: MGO-011, MGO-024, MGO-025
- Cahier des charges: section 6.1

# GATE-INTEGRATIONS-001 - Routage callbacks Mobile Money et service IA

## Priorite

HIGH

## Delai estime

2d

## Dependances

- GATE-AUTH-001
- CORE-PAY-001
- AI-MATCH-001

## Objectif

En tant que plateforme, je veux isoler les callbacks externes et appels IA afin de fiabiliser les integrations critiques.

## Taches

- Exposer routes dediees callbacks MTN MoMo, Orange Money ou agregateur.
- Verifier signature, idempotence et origine des callbacks.
- Router les demandes d'assistance IA vers le microservice FastAPI.
- Ajouter timeouts et circuit breaker pour services externes.

## Criteres d'acceptation

- Un callback non signe ou rejoue est refuse.
- Un callback valide est transmis une seule fois au backend core.
- Une indisponibilite IA renvoie un fallback exploitable par le mobile.
- Les erreurs d'integration sont observables sans fuite de donnees sensibles.

## References

- Backlog: MGO-018, MGO-020
- Cahier des charges: section 5.3, section 6.2

