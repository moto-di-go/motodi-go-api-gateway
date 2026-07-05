# Recherche UX et system design - MOTO'DI GO

Date de synthese : 3 juillet 2026

Ce document consolide l'analyse de la documentation locale dans l'ordre demande, puis liste les references UX/UI et system design directement utiles pour concevoir l'application mobile Flutter et le frontend web d'administration.

## 1. Cahier des charges

Sources locales : `Cahier_des_Charges_MOTO_DI_GO.docx`, `Cahier_des_Charges_MOTO_DI_GO.pdf`.

MOTO'DI GO est une plateforme MaaS pour le Cameroun, centree sur la mise en relation entre passagers, mototaxis, transporteurs interurbains et administrateurs. Le probleme principal est une asymetrie d'information : absence de visibilite sur horaires/tarifs, faible confiance envers les transporteurs, tarification opaque, routes degradees et manque d'itineraires alternatifs.

Axes fonctionnels majeurs :

- Comptes, OTP, profils multi-roles : passager, mototaxi, transporteur, agence, admin.
- KYC et controle documentaire : CNI, permis, carte grise, assurance, photo, documents agence.
- SIG et cartographie : geolocalisation, trafic, routes degradees, itineraire, tarif standardise.
- Interurbain : annonces, recherche multicritere, souscription, blocage de place.
- Wallet : MTN MoMo, Orange Money, ledger, paiement trajet, recu.
- Offline : cartes telechargeables, file d'operations differees, paiement NFC hors-ligne.
- IA : recommandations de covoiturage avec explication et fallback.
- Post-trajet : notation, historique, support.
- Back-office : validation KYC, exploitation, tarification/SIG, moderation, KPI.

Contraintes non fonctionnelles :

- API cible sous 200 ms pour les requetes courantes de geolocalisation.
- Des milliers de terminaux peuvent transmettre une position GPS toutes les 5 secondes.
- Chiffrement TLS 1.3 en transit et AES-256 au repos pour documents/biometrie.
- SLA vise : 99,9 %.
- Forte resilience reseau, car le risque central est l'instabilite Internet.

Point d'attention : le cahier des charges cite Astro + TypeScript + Tailwind + shadcn/ui pour le dashboard, tandis que le backlog mentionne Next.js pour le back-office. Il faut trancher cette divergence avant le design system web.

## 2. Product backlog

Source locale : `Product_Backlog_MOTO_DI_GO.html`.

Le backlog contient 34 user stories structurees en 10 epics :

- Socle technique et API : depots, Docker, CI/CD, modele PostGIS, gateway securisee.
- Identite et profils : inscription OTP, session, profils multi-roles.
- Conformite et securite : KYC, chiffrement, validation admin.
- SIG et tarification : carte, GPS, couches trafic, prix standardise.
- Transport urbain et interurbain : mototaxi proche, annonces, recherche, souscription.
- Agent IA : recommandation de covoiturage.
- Wallet et paiements : wallet, Mobile Money, paiement trajet, NFC offline.
- Reservation et suivi : verrouillage de place, notifications, statut temps reel.
- Hors-ligne et post-trajet : cartes offline, sync, notation, historique, support.
- Back-office et analytics : operations, KPI, QA, deploiement.

Priorites :

- 24 items HIGH : le MVP, la securite, les parcours transport et l'administration essentielle.
- Les items MEDIUM couvrent surtout V1.1 : trafic avance, IA, NFC/offline, support, analytics.
- LOW : mini-jeu offline V1.2.

Roadmap :

- Sprint 1 : fondations, profils, securite, KYC.
- Sprint 2 : SIG, trajets, covoiturage, IA.
- Sprint 3 : wallet, Mobile Money, NFC, offline, reservation.
- Sprint 4 : post-trajet, dashboard, QA, production.

Implication UX : l'application mobile doit etre concue autour de 4 parcours pivot, pas autour des modules techniques : "je me verifie", "je trouve/reserve un trajet", "je paie/suis mon trajet", "je gere les incidents/offline".

## 3. Tickets GitHub

Sources locales : dossier `github_tickets/`.

Repartition par depot :

- `motodi-go-mobile` : 14 tickets couvrant auth/profils, KYC, carte, conducteur GPS, recherche mototaxi, trafic, interurbain, IA, wallet, NFC, reservation/suivi, offline, mini-jeu, notation/support.
- `motodi-go-backend-core` : 19 tickets couvrant modele PostGIS, auth, profils, KYC, geo, pricing, urban, intercity, booking, wallet, paiement, NFC, notifications, tracking, offline, post-trajet, analytics.
- `motodi-go-api-gateway` : 4 tickets sur API versionnee, JWT/RBAC, realtime, callbacks Mobile Money et IA.
- `motodi-go-backend-ai` : 2 tickets sur matching IA et metriques.
- `motodi-go-admin-dashboard` : 7 tickets sur shell admin, KYC, operations, pricing/SIG, moderation, KPI, espace agence.
- `motodi-go-devops` : 4 tickets sur organisation, environnement Docker, CI, release/rollback.

Lecture produit :

- Les tickets mobile imposent une UX multi-role et offline-first.
- Les tickets admin imposent une interface dense, orientee file de travail et audit, plus proche d'un outil d'exploitation que d'un site marketing.
- Les tickets backend imposent des etats metier explicites : validationStatus, bookingStatus, tripStatus, paymentStatus, syncStatus. Ces etats doivent etre visibles et comprehensibles dans l'UI.

## 4. Class diagram

Sources locales : `class-diagramm.pdf`, `mermaid diagrams/diagramme_classes_motodi_go.md`, `mermaid diagrams/description_classes_motodi_go.md`.

Domaines principaux du modele :

- Identite : `User`, `Session`, `OtpCode`, `Profile`, specialisations passager/conducteur/agence/admin.
- KYC : `OfficialDocument`, `ValidationDecision`, `DocumentAccessAudit`, `Vehicle`.
- SIG : `GeoPoint`, `DriverPosition`, `Route`, `TrafficLayer`, `PricingRule`, `FareEstimate`.
- Trajets : `Trip`, `UrbanTrip`, `IntercityTrip`, `IntercityAnnouncement`, `Booking`, `SeatLock`, `TripEvent`.
- Paiement : `Wallet`, `LedgerEntry`, `PaymentTransaction`, `MobileMoneyDeposit`, `RidePayment`, `NfcPaymentToken`, `Receipt`.
- Offline : `OfflineMapArea`, `OfflineOperation`, `SyncBatch`, `SyncConflict`.
- Confiance/support : `Rating`, `SupportTicket`, `ModerationAction`.
- IA/analytics : `AiRecommendationRequest`, `AiRecommendation`, `AiUsageMetric`, `KpiSnapshot`.
- Services applicatifs : Auth, Profile, KYC, Geo, Pricing, Intercity, Booking, Wallet, Payment, NFC, Notification, Tracking, OfflineSync, Support, Rating, AI, Analytics, ApiGateway.

Implication UI : chaque objet persistant critique doit avoir un etat de surface. Exemples : document `UPLOADED/ACCEPTED/REJECTED`, booking `PENDING/CONFIRMED/PAID`, paiement `PENDING_CALLBACK/CONFIRMED/FAILED`, sync `QUEUED/COMPLETED/PARTIAL_SUCCESS`.

## 5. Object diagram

Sources locales : `Object Diagram.pdf`, `Object Diagram.png`, `mermaid diagrams/diagramme_objets_motodi_go.md`.

Scenario concret instancie :

- Aline, passagere, recherche Douala -> Yaounde.
- Jean Bika possede un profil mototaxi et transporteur valide.
- Une annonce interurbaine Douala -> Yaounde est publiee avec 14 places, 3 restantes, prix 9 000 FCFA.
- Aline filtre par ville, quartier, horaire, budget, puis reserve.
- `SeatLock` verrouille une place pour eviter la surreservation.
- Le paiement wallet debite Aline de 9 000 FCFA, credite le transporteur de 8 100 FCFA et garde 900 FCFA de frais plateforme.
- Un recu `MDG-YYYYMMDD-XXXX` est genere.
- Les notifications, carte offline, jeton NFC et operation offline sont rattaches au meme parcours.

Implication UX : ce scenario doit devenir le prototype principal du MVP, car il traverse la plupart des risques : recherche, IA, reservation, paiement, offline, notification, notation.

## 6. Sequence diagrams

Sources locales : dossier `sequence diagrams/` et fichiers Mermaid `diagrammes_sequence_*.md`.

Flux identite/KYC :

- Inscription OTP : telephone, mot de passe, CGU, verification OTP, activation, creation wallet.
- Connexion : login, refresh token, rotation, logout.
- Profil actif : creation, compatibilite, RBAC, navigation adaptee.
- Upload KYC : validation format/taille/malware, chiffrement, stockage objet.
- Validation KYC : file admin, audit d'acces, decision motivee, notification.

Flux SIG/annonces :

- Partage GPS conducteur toutes les 5 secondes apres consentement et validation.
- Recherche mototaxi proche avec positions fraiches, conducteurs valides, distance d'approche, prix estime.
- Calcul tarifaire : route PostGIS, couches trafic, alternatives, regles tarifaires et trace explicable.
- Publication annonce interurbaine avec profil/vehicule valide.
- Recherche/filtres d'annonces, tri, pagination, suggestions d'elargissement et option IA.

Flux IA/reservation/wallet :

- Recommandation IA avec historique anonymise, score, raison, fallback deterministe.
- Souscription annonce, controle places/doublons, notification transporteur.
- Verrouillage transactionnel de place avec lock pessimiste.
- Recharge wallet via Mobile Money, callbacks signes, idempotence.
- Paiement trajet, debit/credit atomique, recu.

Flux offline/suivi/support/admin :

- Paiement NFC offline : jeton signe, expiration, verification locale, synchronisation differee.
- Sync offline : batch, idempotency keys, conflits affichables.
- Notifications : preferences, historique, retry push.
- Suivi trajet : abonnement realtime, position conducteur, statuts.
- Fin trajet : event, notification, recu, notation.
- Support : anti-spam, ticket, accuse reception, file admin.
- Admin : moderation, operations, tarification/SIG, KPI, export CSV.
- Deploiement : GitHub Actions, registry, migrations, healthchecks, rollback.

Implication UX : les ecrans doivent montrer le "pourquoi" d'un etat, pas seulement l'etat. Exemple : "Place expiree car paiement non confirme avant 08:49", "Document rejete : photo illisible", "Action offline synchronisee sauf paiement NFC en conflit".

## 7. Component diagram

Sources locales : `components' diagram.png`, `mermaid diagrams/diagramme_composants_motodi_go.md`.

Architecture logique :

- Clients : application mobile Flutter et dashboard web admin.
- API edge : Spring Cloud Gateway, REST `/api/v1`, JWT/RBAC, realtime, callbacks externes.
- Backend core : services metier decoupes par domaine.
- Backend IA : FastAPI pour recommandations et metriques IA.
- Donnees : PostgreSQL/PostGIS, stockage objet chiffre, cache mobile local.
- Externes : MTN MoMo, Orange Money, agregateur paiement, FCM/APNs, Mapbox/OpenStreetMap.
- DevOps : Docker Compose, GitHub Actions, VPS, logs/metrics/healthchecks.

Implication system design : le MVP peut rester simple si le backend core est un service deploye unique avec modules internes clairement separes, plus un service IA isole. La decomposition en microservices physiques peut venir apres stabilisation des contrats.

## 8. Deployment diagram

Sources locales : `Deployment Diagram.png`, `mermaid diagrams/diagramme_deploiement_motodi_go.md`.

Cible MVP :

- Mobile Android/iOS avec cache local, cartes offline, file offline, jetons NFC, GPS, push.
- Navigateur admin.
- DNS + TLS public.
- VPS Ubuntu avec reverse proxy, Docker Compose, containers gateway/core/IA/admin/Postgres/MinIO/observability.
- Volumes persistants et backups.
- Fournisseurs externes Mobile Money, push et cartes.
- GitHub Actions pour build, tests, scans, push images, deploy SSH, healthchecks et rollback.

Risques system :

- Le VPS unique simplifie le MVP mais concentre le risque de disponibilite.
- Les callbacks paiement doivent passer par gateway avec signature, idempotence et correlation-id.
- Le stockage objet KYC et les volumes Postgres doivent etre sauvegardes ensemble pour garder la coherence metadonnees/fichiers.
- Le realtime doit avoir une strategie degradable : WebSocket puis SSE/polling selon reseau.

## References UX mobile utiles

| Reference | Description | Ce qu'il faut reprendre pour MOTO'DI GO |
|---|---|---|
| Material Design 3 - https://m3.material.io/ | Design system Android moderne : navigation, composants, etats, accessibilite, theming. | Base pour Flutter : navigation bar, bottom sheets, chips de filtres, cards de trajets, text fields, snackbar, states. |
| Flutter Material widgets - https://docs.flutter.dev/ui/widgets/material | Catalogue officiel des widgets Material dans Flutter. | Aligner les composants mobile avec les widgets natifs Flutter au lieu de recreer des composants custom fragiles. |
| Apple Human Interface Guidelines - https://developer.apple.com/design/human-interface-guidelines/ | Principes iOS : navigation, privacy, feedback, controls. | Adapter les permissions GPS/NFC, dialogues, formulaires et feedbacks iOS. |
| Android location permissions - https://developer.android.com/develop/sensors-and-location/location/permissions | Documentation officielle des permissions localisation Android. | Construire des ecrans de permission GPS transparents pour passager et conducteur, avec refus gere proprement. |
| WCAG 2.2 - https://www.w3.org/TR/WCAG22/ | Standard accessibilite web/mobile. | Contrastes, focus, messages d'erreur, status messages, taille tactile, formulaires accessibles. |
| Uber rider flow - https://www.uber.com/us/en/ride/how-it-works/ | Reference ride-hailing : depart/destination, prix avant commande, conducteur, suivi et paiement. | Home map-first, saisie origine/destination, estimation avant engagement, suivi conducteur, recu. |
| Bolt rides - https://bolt.eu/en/rides/ | Reference ride-hailing simple et rapide. | Reduire les frictions du parcours mototaxi : destination, categorie, prix, confirmation, suivi. |
| Grab Transport - https://www.grab.com/sg/transport/ | Super-app d'Asie du Sud-Est : transport, wallet, promos, services multiples. | Gérer l'ambition multi-service sans perdre la priorite transport : onglets clairs, wallet visible, notifications transactionnelles. |
| Gojek - https://www.gojek.com/en-id/ | Super-app avec transport moto, paiement et services locaux. | Inspiration forte pour un contexte moto/urbain : actions rapides, statut conducteur, paiement integre. |
| inDrive - https://indrive.com/ | Ride-hailing avec logique de choix et negociation. | A ne pas copier tel quel si le prix doit etre standardise, mais utile pour comparer des offres et rendre le prix explicable. |
| Yango - https://yango.com/ | Ride-hailing oriente simplicite, geolocalisation et categories de trajets. | Interface carte, categories, ETA et transparence passager. |
| BlaBlaCar - https://www.blablacar.com/ | Covoiturage/interurbain : recherche ville/date, details conducteur, places, reservation. | Mode interurbain : listes filtrables, confiance, profils, avis, places restantes, conditions depart/arrivee. |
| Moovit - https://moovitapp.com/ | Mobilite urbaine et transport public avec itineraires et alertes. | UX d'itineraire, perturbations, alternatives, et informations temps reel lisibles. |
| Citymapper - https://citymapper.com/ | Navigation multimodale et comparatif d'options. | Comparaison d'options par temps/prix/fiabilite, utile pour carte SIG et itineraires alternatifs. |
| Mapbox mobile/offline docs - https://docs.mapbox.com/ | Cartographie, navigation, tuiles, offline packs selon SDK. | Telechargement zones, rendu carte performant, offline maps, style cartographique. |
| OpenStreetMap - https://www.openstreetmap.org/ | Donnees cartographiques ouvertes. | Option de fond de carte et ecosysteme local, utile si le cout Mapbox devient un risque. |
| Firebase Cloud Messaging - https://firebase.google.com/docs/cloud-messaging | Push Android/iOS/Web. | Notifications reservation, paiement, statut, KYC, support, retries et token device. |
| GSMA Mobile Money API - https://developer.mobilemoneyapi.io/ | Standardisation d'APIs Mobile Money. | Structure des parcours de paiement, statuts, callbacks, reconciliation. |
| MTN MoMo Developer - https://momodeveloper.mtn.com/ | API officielle MTN Mobile Money. | Parcours recharge wallet, statut pending, callback, reference externe. |
| Orange Developer - https://developer.orange.com/apis/orange-money-web-payment | API Orange Money Web Payment selon disponibilite pays. | Variante Orange Money, erreurs operateur, redirections/confirmation. |

## UX mobile recommande

Architecture d'information mobile :

- Accueil passager : carte, barre origine/destination, bouton mode offline, wallet compact, notifications.
- Accueil conducteur : switch disponibilite, statut KYC, partage GPS, courses actives, consignes batterie/reseau.
- Accueil transporteur/agence : publier annonce, annonces actives, reservations, revenus, vehicules.
- Onglets : Accueil, Trajets, Wallet, Messages/Notifications, Profil.
- Role switcher : visible mais controle, avec permissions et statut de validation.

Parcours prioritaires a prototyper :

1. Onboarding, OTP, choix profil, KYC.
2. Recherche mototaxi urbain avec prix standardise.
3. Recherche interurbaine Douala -> Yaounde avec filtres, IA et reservation.
4. Recharge wallet, paiement, recu.
5. Trajet actif avec suivi temps reel.
6. Offline : carte telechargee, paiement NFC pending, sync et conflit.
7. Notation/support.

Patterns cles :

- Map-first avec bottom sheet : evite une page chargee et laisse la carte visible.
- Prix explicable : "base + distance + coefficient nuit/meteo + arrondi".
- Etats persistants : pending, confirme, paye, en attente sync, conflit.
- Feedback reseau : online/offline, action mise en file, derniere sync.
- KYC en checklist : pieces requises par profil, statut par document, motif de rejet.
- Notification center : toutes les notifications importantes doivent etre historisees, pas seulement poussees.
- Localisation progressive : demander le GPS au moment utile, expliquer l'usage avant le prompt OS.
- Paiement prudent : confirmation claire, solde avant/apres, recu et idempotence visible par reference.

## References UX frontend web/admin

| Reference | Description | Ce qu'il faut reprendre pour MOTO'DI GO |
|---|---|---|
| shadcn/ui - https://ui.shadcn.com/docs | Composants React/Tailwind copiables, adaptés aux dashboards modernes. | Tables, dialogs, forms, tabs, command palette, side navigation, badges d'etat. |
| Tailwind CSS - https://tailwindcss.com/docs | Framework utilitaire CSS. | Systeme visuel sobre, responsive, rapide a maintenir. |
| Ant Design - https://ant.design/ | Design system admin/data dense. | Tables, filtres, formulaires, steps, upload, notifications, data display. |
| PatternFly - https://www.patternfly.org/ | Design system open source pour consoles d'administration. | UX d'exploitation : alertes, resource lists, dashboards, etats systeme. |
| Carbon Design System - https://carbondesignsystem.com/ | Design system enterprise IBM. | Data tables, empty states, modales, charts, accessibilite. |
| Metabase - https://www.metabase.com/docs/latest/ | BI/dashboard open source. | KPI, filtres periode, graphiques, export, exploration simple. |
| Apache Superset - https://superset.apache.org/docs/intro | BI open source plus avance. | Tableaux de bord analytics, datasets, permissions, exports. |
| Mapbox GL JS - https://docs.mapbox.com/mapbox-gl-js/ | Cartes web interactives. | Previsualisation couches SIG, dessin de zones, affichage trafic/routes degradees. |
| Leaflet - https://leafletjs.com/ | Cartographie web open source. | Alternative plus simple et legere pour dashboard SIG MVP. |

UX web/admin recommande :

- Dashboard operations : KPI compacts en haut, puis files de travail et listes filtrables.
- KYC queue : colonne "en attente", preview document, checklist pieces, decision avec motif obligatoire, audit d'acces.
- Operations : utilisateurs/profils, annonces, reservations, transactions, filtres par statut, recherche, pagination, export.
- SIG/tarification : editeur de regles tarifaires, preview carte, validation geometrie, historique avant/apres.
- Support/moderation : file unifiee avis/tickets/signalements, actions rapides, escalade.
- KPI : adoption, conducteurs actifs, reservations, volumes wallet, latence IA, erreurs sync, SLA, exports CSV.
- RBAC : les menus et actions visibles doivent suivre le role admin/agence/support/regulateur.
- Audit-first : toutes les decisions sensibles doivent afficher auteur, date, raison, correlation-id.

## References system design utiles

| Reference | Description | Application a MOTO'DI GO |
|---|---|---|
| Uber H3 - https://www.uber.com/blog/h3/ et https://h3geo.org/docs/ | Indexation geospatiale hexagonale open source. | Clustering positions, zones, heatmaps, recherche approximative, analytics congestion. |
| PostGIS ST_DWithin - https://postgis.net/docs/ST_DWithin.html | Fonction de proximite geospatiale avec index. | Recherche conducteurs proches, couches trafic, zones, distances. |
| PostgreSQL indexes - https://www.postgresql.org/docs/current/indexes.html | Index B-tree/GiST/GIN selon besoins. | Index spatiaux, recherche annonces, contraintes unicite idempotency key. |
| Spring Cloud Gateway - https://docs.spring.io/spring-cloud-gateway/reference/ | Gateway reactive, routing, filters. | `/api/v1`, JWT/RBAC, rate limit, correlation-id, callbacks. |
| Spring Security - https://docs.spring.io/spring-security/reference/ | Auth, authorization, JWT/OAuth patterns. | Securite API, roles, permissions, session/token lifecycle. |
| FastAPI - https://fastapi.tiangolo.com/ | Framework Python API rapide. | Backend IA avec contrats OpenAPI et fallback. |
| Apache Kafka docs - https://kafka.apache.org/documentation/ | Event streaming durable. | A prevoir apres MVP pour GPS, notifications, paiement, analytics; au MVP, un broker plus simple peut suffire. |
| Firebase Cloud Messaging - https://firebase.google.com/docs/cloud-messaging | Push notifications cross-platform. | Notifications transactionnelles, retries, tokens device, preference utilisateur. |
| Apple Push Notification service - https://developer.apple.com/documentation/usernotifications | Notifications iOS. | Contraintes iOS pour push et permissions. |
| Stripe idempotency - https://docs.stripe.com/api/idempotent_requests | Reference claire pour idempotency keys. | Depots Mobile Money, callbacks, paiement wallet, sync offline. |
| Stripe webhooks - https://docs.stripe.com/webhooks | Bonnes pratiques webhooks. | Signature callbacks, retries, ordre d'evenements, reconciliation paiement. |
| OWASP API Security Top 10 - https://owasp.org/API-Security/ | Risques API modernes. | Gateway, RBAC, rate limiting, validation objet, exposition donnees sensibles. |
| OWASP MASVS - https://mas.owasp.org/MASVS/ | Standard securite mobile. | Stockage tokens, chiffrement local, NFC, offline queue, anti-tampering. |
| NIST SP 800-63B - https://pages.nist.gov/800-63-3/sp800-63b.html | Identite numerique et authentification. | Politique mot de passe, OTP, session, recuperation acces. |
| MinIO docs - https://min.io/docs/minio/linux/index.html | Stockage objet S3 compatible auto-hebergeable. | Documents KYC chiffres, recus PDF, backups. |
| Docker Compose docs - https://docs.docker.com/compose/ | Orchestration locale/VPS simple. | Cible MVP sur VPS Ubuntu. |
| OpenTelemetry - https://opentelemetry.io/docs/ | Traces, metrics, logs standards. | Correlation-id, latence API, callbacks paiement, flux IA, sync offline. |
| Prometheus - https://prometheus.io/docs/introduction/overview/ | Metrics et alerting. | Healthchecks, SLA, latence, erreurs, saturation VPS. |

## System design recommande

Design logique MVP :

- Garder un `backend-core` Spring Boot modulaire avec packages/services internes par domaine.
- Isoler physiquement `api-gateway`, `backend-core`, `backend-ai`, `admin-dashboard`, `postgres/postgis`, `object-storage`.
- Eviter une multiplication de microservices au MVP si l'equipe est petite ; les contrats internes peuvent rester propres sans complexifier le deploiement.

Donnees :

- PostgreSQL/PostGIS comme source de verite metier et geospatiale.
- Tables critiques : users, profiles, documents, vehicles, positions, routes, pricing_rules, announcements, bookings, seat_locks, wallets, ledger_entries, payments, notifications, offline_operations, support_tickets, ratings, kpi_snapshots.
- Utiliser GiST/SP-GiST pour geometries, B-tree pour statuts/dates, unicite sur idempotency keys.
- Ledger append-only : ne jamais modifier une ecriture comptable; corriger par reversal.

Realtime :

- Gateway expose WebSocket/SSE; fallback polling mobile en reseau degrade.
- Position conducteur : echantillonnage 5 s, filtrage frequence, precision minimale, consentement GPS, stockage position fraiche et historique limite.
- Les evenements trajet alimentent notifications et historique.

Paiement :

- Mobile Money : initier transaction, stocker `externalReference`, attendre callback signe.
- Idempotence obligatoire sur depot, callback, paiement trajet, sync offline.
- Wallet : debit/credit atomique en transaction DB, ledger immuable, reconciliation admin.
- NFC offline : jeton signe, plafond, expiration courte, anti-rejeu, regularisation serveur.

Reservation :

- `SeatLock` en transaction avec verrou pessimiste sur annonce.
- Expiration automatique des locks non payes/confirmes.
- UX doit exposer le compte a rebours de reservation.

Offline :

- Mobile stocke cartes, donnees essentielles, file d'operations et jetons NFC.
- Chaque operation offline porte un type, payload, idempotency key, statut, retryCount.
- Sync batch retourne un rapport lisible : synchronise, ignore idempotent, conflit, erreur retry.

KYC/securite :

- Fichiers chiffres avant stockage objet, metadonnees en DB.
- Audit d'acces documents obligatoire.
- Ne pas exposer les URLs objet directement sans URL signee/courte duree.
- Separation stricte des permissions admin : KYC, support, pricing, payment, KPI.

Observabilite :

- Correlation-id de l'app mobile jusqu'au backend et callbacks.
- Metrics prioritaires : latence API, erreurs paiement, callbacks invalides, temps sync offline, position ingestion rate, push failure rate, DB slow queries, stockage, CPU/RAM VPS.
- Logs sans PII sensible; masquage telephone/documents.

Evolution apres MVP :

- Ajouter broker evenementiel si la charge GPS/notifications/analytics augmente.
- Separer Postgres du VPS si SLA et volumetrie deviennent critiques.
- Passer vers Kubernetes seulement si l'operation Docker Compose devient un frein reel.

## Priorite de conception

1. Prototype mobile passager complet Douala -> Yaounde : recherche, filtres, IA, reservation, paiement, recu, notification.
2. Prototype mobile conducteur/transporteur : KYC, disponibilite GPS, annonce, reservations, statut trajet.
3. Prototype offline : carte telechargee, NFC pending, sync, conflit.
4. Prototype dashboard KYC + operations + paiement : files, decisions, audit, filtres.
5. Prototype dashboard SIG/tarif + KPI : carte, regles, export.

La cle UX du projet est la confiance : identite verifiee, prix explicable, paiement tracable, statut visible et fonctionnement comprehensible en reseau degrade.
