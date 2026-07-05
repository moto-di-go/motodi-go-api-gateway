# Description des classes et interactions - MOTO'DI GO

Ce document complete `diagramme_classes_motodi_go_annote.md`. Il decrit le role, la categorie technique et les interactions principales de chaque classe. Les attributs sont volontairement notes avec `-` dans le diagramme pour indiquer une visibilite privee, tandis que les operations restent publiques avec `+`.


Categories utilisees :

- `persistante BD` : classe stockee en base de donnees.
- `service backend-core` : service applicatif du backend-core, nomenclature `xxxService`.
- `enumeration` : classe de constantes, types ou statuts.
- `IA` : classe liee au module d'intelligence artificielle.
- `abstraite` : classe mere non instanciee directement.
- `objet valeur / DTO` : objet de support non prevu comme table autonome.
- `gateway` : composant d'entree API, hors backend-core metier.
## 1. Identite, acces et profils

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `User` | persistante BD | Represente le compte principal d'un utilisateur inscrit avec telephone, mot de passe, statut et acceptation des CGU. | Compose `Profile`, `Session`, `OtpCode`, `Wallet`, `Notification`, `NotificationPreference`, `OfflineMapArea`, `OfflineOperation` et `SupportTicket`; cree des `Rating`; sert de base aux KPI MAU. |
| `Session` | persistante BD | Represente une session authentifiee avec access token, refresh token, appareil et expiration. | Appartient a un `User`; creee, rafraichie et revoquee par `AuthService`; son etat est controle par `SessionStatus`. |
| `OtpCode` | persistante BD | Porte un code OTP hashé pour inscription, connexion, recuperation ou confirmation sensible. | Appartient a un `User`; emis et verifie par `AuthService`; qualifie par `OtpPurpose`. |
| `Profile` | persistante BD, abstraite | Classe abstraite des profils metiers et administratifs d'un compte. | Composee par `User`; specialisee par `PassengerProfile`, `DriverProfile`, `AgencyProfile`, `AdminProfile`; possede `OfficialDocument` et `ValidationDecision`; recoit des `Rating`. |
| `PassengerProfile` | persistante BD | Profil d'un passager urbain ou voyageur interurbain. | Herite de `Profile`; cree des `Booking`; participe aux `UrbanTrip`; demande des estimations via `PricingService` et peut payer via `PaymentService`. |
| `DriverProfile` | persistante BD, abstraite | Classe abstraite des profils conducteurs avec disponibilite et consentement GPS. | Herite de `Profile`; parent de `MotoTaxiProfile` et `TransporterProfile`; publie des `DriverPosition`; utilise des `Vehicle`; controle par `DriverAvailability`. |
| `MotoTaxiProfile` | persistante BD | Conducteur urbain de mototaxi. | Herite de `DriverProfile`; affecte aux `UrbanTrip`; visible via `GeoService` et `CORE-URBAN`; partage sa position en temps reel. |
| `TransporterProfile` | persistante BD | Conducteur ou transporteur interurbain valide. | Herite de `DriverProfile`; publie des `IntercityAnnouncement`; assure des `IntercityTrip`; recoit les fonds de `RidePayment`. |
| `AgencyProfile` | persistante BD | Profil d'une agence partenaire qui gere flotte, annonces et statistiques. | Herite de `Profile`; gere plusieurs `Vehicle`; publie des `IntercityAnnouncement`; consulte les KPI et l'espace agence. |
| `AdminProfile` | persistante BD | Profil d'administration, support ou regulation. | Herite de `Profile`; prend des `ValidationDecision`, consulte `DocumentAccessAudit`, traite `SupportTicket`, effectue `ModerationAction`; qualifie par `AdminRole`. |

## 2. KYC, documents et flotte

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `Vehicle` | persistante BD | Vehicule exploite par un conducteur ou une agence, avec type, immatriculation et capacite. | Agrege par `DriverProfile` ou `AgencyProfile`; affecte a des `IntercityAnnouncement`; son type est `VehicleType`. |
| `OfficialDocument` | persistante BD | Document officiel ou biometrique televerse pour verifier un profil ou un vehicule. | Compose par `Profile`; chiffre et controle par `KycService`; audite via `DocumentAccessAudit`; typé par `DocumentType` et `DocumentStatus`. |
| `ValidationDecision` | persistante BD | Decision administrative de validation, rejet ou suspension d'un profil. | Composee dans l'historique de `Profile`; emise par `AdminProfile`; appliquee par `KycService`; declenche une `Notification`. |
| `DocumentAccessAudit` | persistante BD | Journal d'acces aux documents sensibles. | Compose par `OfficialDocument`; produit lors des consultations admin; relie `AdminProfile`, action d'acces et correlation technique. |

## 3. SIG, positions, routes et tarification

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `GeoPoint` | objet valeur / DTO | Valeur geospatiale latitude/longitude/PostGIS. | Utilisee par `DriverPosition`, `Route`, `Trip`, `PricingService` et les recherches de proximite. |
| `DriverPosition` | persistante BD | Position GPS publiee par un conducteur avec precision, vitesse et horodatage. | Composee par `DriverProfile`; ingeree par `GeoService`; exposee a `TrackingService`, `UrbanTrip` et recherche mototaxi. |
| `Route` | persistante BD | Itineraire entre origine et destination, distance, duree et statut. | Compose `RouteAlternative`; reference des `TrafficLayer`; utilisee par `FareEstimate`. |
| `RouteAlternative` | persistante BD | Itineraire de secours propose si la route principale est bloquee ou degradee. | Composee par `Route`; declenchee par les donnees `TrafficLayer`. |
| `TrafficLayer` | persistante BD | Couche SIG administrable : congestion, travaux, route degradee ou voie alternative. | Publiee par l'administration via `GeoService`/`ADMIN-PRICING`; consommee par `Route` et les cartes mobiles. |
| `PricingRule` | persistante BD | Regle tarifaire active par zone, creneau, distance et coefficients. | Parametree par admin; appliquee par `PricingService`; produit des `FareEstimate`; utilise `TimeWindow`. |
| `FareEstimate` | persistante BD | Estimation de prix, distance, duree et trace des regles appliquees. | Produite par `PricingRule` et `PricingService`; liee a `Route`; referencee par `Trip` avant paiement. |

## 4. Trajets, annonces et reservations

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `Trip` | persistante BD, abstraite | Classe abstraite d'un trajet urbain ou interurbain avec statut et prix final. | Parent de `UrbanTrip` et `IntercityTrip`; compose `TripEvent`; reference `FareEstimate` et `RidePayment`; autorise les `Rating` apres fin. |
| `UrbanTrip` | persistante BD | Course urbaine entre passager et mototaxi. | Herite de `Trip`; relie `PassengerProfile` et `MotoTaxiProfile`; utilise position conducteur et estimation tarifaire. |
| `IntercityTrip` | persistante BD | Trajet interurbain avec villes, quartier et places disponibles. | Herite de `Trip`; prepare par `IntercityAnnouncement`; exploite par `TransporterProfile`; relie les `Booking`. |
| `IntercityAnnouncement` | persistante BD | Annonce interurbaine publiee avec horaire, prix, places, vehicule et consignes. | Publiee par `TransporterProfile` ou `AgencyProfile`; affecte un `Vehicle`; compose des `Booking`; analysee par `AiRecommendationRequest`. |
| `AnnouncementSearchCriteria` | objet valeur / DTO | Objet de criteres pour filtrer les annonces par ville, date, prix, horaire, quartier et disponibilite. | Utilise par `IntercityService`; filtre `IntercityAnnouncement`; contient `TimeRange`. |
| `Booking` | persistante BD | Demande de participation ou reservation avec statut, expiration et nombre de places. | Creee par `PassengerProfile`; composee par `IntercityAnnouncement`; verrouillee par `SeatLock`; liee a `Trip`, `RidePayment`, `Notification` et `OfflineOperation`. |
| `SeatLock` | persistante BD | Verrou transactionnel de places pour eviter la surreservation concurrente. | Compose par `Booking`; manipule par `BookingService`; consomme ou libere selon paiement/expiration. |
| `TripEvent` | persistante BD | Evenement horodate de changement de statut de trajet. | Compose par `Trip`; cree par `TrackingService`; alimente l'historique et les notifications. |

## 5. Wallet, paiements et recus

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `Wallet` | persistante BD | Portefeuille electronique unique d'un utilisateur, avec solde et devise FCFA. | Compose par `User`; compose `LedgerEntry`; credite par `MobileMoneyDeposit`, debite/credite par `RidePayment`, reserve par `NfcPaymentToken`; gere par `WalletService`. |
| `LedgerEntry` | persistante BD | Ecriture comptable immuable de debit, credit, hold, release ou reversal. | Composee par `Wallet`; creee par `PaymentTransaction`; garantit audit, idempotence et coherence du solde. |
| `PaymentTransaction` | persistante BD, abstraite | Classe abstraite des transactions de paiement. | Parent de `MobileMoneyDeposit` et `RidePayment`; comptabilise des `LedgerEntry`; genere un `Receipt`; son etat est `PaymentStatus`. |
| `MobileMoneyDeposit` | persistante BD | Depot wallet via MTN MoMo, Orange Money ou agregateur. | Herite de `PaymentTransaction`; credite `Wallet`; recoit callbacks via `ApiGateway` et `PaymentService`; utilise `PaymentProvider`. |
| `RidePayment` | persistante BD | Paiement d'un trajet ou d'une reservation avec repartition passager/transporteur. | Herite de `PaymentTransaction`; debite le wallet passager, credite le wallet conducteur/transporteur; regle `Booking` ou finalise `Trip`. |
| `NfcPaymentToken` | persistante BD | Jeton signe de paiement hors-ligne, limite dans le temps et anti-rejeu. | Reserve un montant sur `Wallet`; regularise une `PaymentTransaction`; synchronise via `OfflineSyncService` et `NfcPaymentService`. |
| `Receipt` | persistante BD | Recu produit apres paiement confirme. | Compose par `PaymentTransaction`; genere par `PaymentService`; visible dans historique de trajet et wallet. |

## 6. Notifications et preferences

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `Notification` | persistante BD | Message push ou historique minimal envoye a un utilisateur. | Composee par `User`; reference optionnellement `Booking`, `PaymentTransaction` ou `Trip`; envoyee par `NotificationService`. |
| `NotificationPreference` | persistante BD | Preferences utilisateur pour push, reservations, paiements et marketing. | Composee par `User`; consultee par `NotificationService` avant envoi. |

## 7. Mode hors-ligne, cache et synchronisation

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `OfflineMapArea` | persistante BD | Zone de carte telechargee pour usage sans reseau. | Composee par `User`; alimente le parcours offline mobile. |
| `OfflineOperation` | persistante BD | Action realisee hors connexion et a rejouer au retour reseau. | Composee par `User`; synchronisee par `SyncBatch`; peut viser `Booking` ou `NfcPaymentToken`; peut produire un `SyncConflict`. |
| `SyncBatch` | persistante BD | Lot de synchronisation d'operations offline. | Compose plusieurs `OfflineOperation`; orchestre par `OfflineSyncService`; qualifie par `SyncStatus`. |
| `SyncConflict` | persistante BD | Conflit detecte pendant une synchronisation differee. | Compose par `OfflineOperation`; explique a l'utilisateur et resolu par `OfflineSyncService`. |

## 8. Post-trajet, support et moderation

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `Rating` | persistante BD | Note et commentaire reciproques apres trajet termine. | Autorise par `Trip`; cree par `User`; cible un `Profile`; modere par `ModerationAction`; gere par `RatingService`. |
| `SupportTicket` | persistante BD | Ticket support categorise et priorise. | Compose par `User`; traite par `AdminProfile`; peut etre cible par `ModerationAction`; gere par `SupportService`. |
| `ModerationAction` | persistante BD | Action administrative sur avis, ticket, utilisateur, profil ou annonce. | Effectuee par `AdminProfile`; cible `Rating` ou `SupportTicket` dans le diagramme; qualifiee par `ModerationTargetType` et `ModerationActionType`. |

## 9. IA et analytics

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `AiRecommendationRequest` | persistante BD, IA | Requete anonymisee de recommandation IA pour le covoiturage interurbain. | Analyse des `IntercityAnnouncement`; contient les contraintes voyageur; produit des `AiRecommendation`. |
| `AiRecommendation` | persistante BD, IA | Suggestion IA avec score, raison lisible et annonce recommandee. | Composee par `AiRecommendationRequest`; pointe vers une `IntercityAnnouncement`; agregee dans `AiUsageMetric`. |
| `AiUsageMetric` | persistante BD, IA | Indicateurs d'utilisation, latence, erreurs et suggestions acceptees de l'IA. | Agrege `AiRecommendation`; consommee par `KpiSnapshot` et `AnalyticsService`. |
| `KpiSnapshot` | persistante BD | Snapshot KPI produit, technique et financier sur une periode. | Depend de `User`, `DriverProfile`, `Booking`, `PaymentTransaction` et `AiUsageMetric`; calcule et exporte les indicateurs admin. |

## 10. Services applicatifs et gateway

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `AuthService` | service backend-core | Orchestre inscription, OTP, login, refresh, logout et reset mot de passe. | Cree/controle `User`, `OtpCode`, `Session`; appele par `ApiGateway`. |
| `ProfileService` | service backend-core | Gere creation, edition, selection et permissions des profils. | Manipule `Profile`; utilise par `ApiGateway` pour RBAC. |
| `KycService` | service backend-core | Gere documents, chiffrement, exigences par profil et validation admin. | Manipule `OfficialDocument`, `ValidationDecision`, `DocumentAccessAudit`; appelle `NotificationService`. |
| `GeoService` | service backend-core | Gere ingestion GPS, dernieres positions, recherche de proximite et couches SIG. | Manipule `DriverPosition`, `TrafficLayer`, `GeoPoint`; alimente `TrackingService` et recherche urbaine. |
| `PricingService` | service backend-core | Calcule distance, duree, prix et trace tarifaire. | Utilise `Route`, `PricingRule`, `FareEstimate`; appele par recherche urbaine, annonces et paiement. |
| `IntercityService` | service backend-core | Gere publication, mise a jour, fermeture et recherche d'annonces. | Manipule `IntercityAnnouncement` et `AnnouncementSearchCriteria`; utilise par IA et mobile. |
| `BookingService` | service backend-core | Gere demande, confirmation, annulation, expiration et verrouillage des places. | Manipule `Booking` et `SeatLock`; peut etendre vers `PaymentService` si paiement requis. |
| `WalletService` | service backend-core | Gere wallet, solde et ledger. | Manipule `Wallet` et `LedgerEntry`; appele par `PaymentService`. |
| `PaymentService` | service backend-core | Orchestre depot Mobile Money, callbacks, paiement trajet et recus. | Manipule `MobileMoneyDeposit`, `RidePayment`, `Receipt`; depend de `WalletService`; appele par `ApiGateway` pour callbacks. |
| `NfcPaymentService` | service backend-core | Gere emission, echange et validation des jetons NFC hors-ligne. | Manipule `NfcPaymentToken`; etend `PaymentService`; appele par `OfflineSyncService`. |
| `NotificationService` | service backend-core | Envoie notifications, applique preferences et relance les echecs. | Manipule `Notification` et `NotificationPreference`; appele par KYC, booking, paiement et tracking. |
| `TrackingService` | service backend-core | Gere transitions de statut, suivi conducteur et cloture de trajet. | Manipule `Trip`, `TripEvent`, `DriverPosition`; peut declencher paiement final via `PaymentService`. |
| `OfflineSyncService` | service backend-core | Synchronise les operations offline avec idempotence et resolution de conflits. | Manipule `SyncBatch`, `OfflineOperation`, `SyncConflict`; appelle `BookingService` et `NfcPaymentService`. |
| `SupportService` | service backend-core | Gere creation, reponse et changement de statut des tickets support. | Manipule `SupportTicket`; travaille avec `AdminProfile` et notifications support. |
| `RatingService` | service backend-core | Gere notation post-trajet, score moyen et signalement. | Manipule `Rating`; recalcule le score de `Profile`; expose les avis a moderation. |
| `AiRecommendationService` | IA | Produit des recommandations de covoiturage et un fallback deterministe. | Manipule `AiRecommendationRequest`, `AiRecommendation`; depend de `IntercityService`; alimente `AnalyticsService`. |
| `AnalyticsService` | service backend-core | Calcule KPI, metriques IA, exports CSV et anonymisation. | Manipule `KpiSnapshot`, `AiUsageMetric`; consomme donnees utilisateurs, reservations, paiements et IA. |
| `ApiGateway` | gateway | Point d'entree API versionne, securise et observable. | Route vers `AuthService`, `ProfileService`, `PaymentService`, `AiRecommendationService`; applique JWT, RBAC, rate limiting et verification callbacks. |

## 11. Types, statuts et enumerations

| Classe | Categorie | Role | Interactions principales |
|---|---|---|---|
| `UserStatus` | enumeration | Cycle de vie d'un compte utilisateur. | Contraint `User`. |
| `SessionStatus` | enumeration | Etat d'une session. | Contraint `Session`. |
| `OtpPurpose` | enumeration | Motif d'emission d'un OTP. | Contraint `OtpCode`. |
| `ProfileType` | enumeration | Type de profil actif ou cree. | Contraint `Profile`. |
| `AdminRole` | enumeration | Role administratif precis. | Contraint `AdminProfile`. |
| `ValidationStatus` | enumeration | Etat de validation d'un profil ou vehicule. | Contraint `Profile` et `Vehicle`. |
| `DocumentType` | enumeration | Type de piece officielle attendue. | Contraint `OfficialDocument`. |
| `DocumentStatus` | enumeration | Etat d'un document dans le workflow KYC. | Contraint `OfficialDocument`. |
| `VehicleType` | enumeration | Categorie de vehicule. | Contraint `Vehicle`. |
| `DecisionType` | enumeration | Nature d'une decision admin. | Contraint `ValidationDecision`. |
| `AccessAction` | enumeration | Type d'acces audite a un document. | Contraint `DocumentAccessAudit`. |
| `DriverAvailability` | enumeration | Disponibilite conducteur. | Contraint `DriverProfile`. |
| `RouteStatus` | enumeration | Etat d'un itineraire. | Contraint `Route`. |
| `TrafficLayerType` | enumeration | Type de couche SIG. | Contraint `TrafficLayer`. |
| `Severity` | enumeration | Gravite d'un incident ou couche trafic. | Contraint `TrafficLayer`. |
| `TimeWindow` | objet valeur / DTO | Fenetre horaire d'application tarifaire. | Utilisee par `PricingRule`. |
| `TimeRange` | objet valeur / DTO | Intervalle horaire de recherche. | Utilisee par `AnnouncementSearchCriteria`. |
| `AnnouncementStatus` | enumeration | Etat d'une annonce interurbaine. | Contraint `IntercityAnnouncement`. |
| `BookingStatus` | enumeration | Etat d'une reservation. | Contraint `Booking`. |
| `TripType` | enumeration | Type de trajet. | Contraint `Trip`. |
| `TripStatus` | enumeration | Statut operationnel d'un trajet. | Contraint `Trip` et `TripEvent`. |
| `WalletStatus` | enumeration | Etat du wallet. | Contraint `Wallet`. |
| `LedgerEntryType` | enumeration | Type d'ecriture comptable. | Contraint `LedgerEntry`. |
| `PaymentType` | enumeration | Type de transaction. | Contraint `PaymentTransaction`. |
| `PaymentStatus` | enumeration | Etat d'une transaction. | Contraint `PaymentTransaction`. |
| `PaymentProvider` | enumeration | Fournisseur de paiement. | Contraint `MobileMoneyDeposit`. |
| `NfcTokenStatus` | enumeration | Etat d'un jeton NFC. | Contraint `NfcPaymentToken`. |
| `NotificationType` | enumeration | Type de notification. | Contraint `Notification`. |
| `NotificationStatus` | enumeration | Etat de livraison ou lecture d'une notification. | Contraint `Notification`. |
| `OfflineOperationType` | enumeration | Type d'action offline. | Contraint `OfflineOperation`. |
| `OfflineOperationStatus` | enumeration | Etat de synchronisation d'une operation offline. | Contraint `OfflineOperation`. |
| `SyncStatus` | enumeration | Etat global d'un lot de synchronisation. | Contraint `SyncBatch`. |
| `RatingStatus` | enumeration | Etat de publication ou moderation d'un avis. | Contraint `Rating`. |
| `SupportCategory` | enumeration | Categorie d'un ticket support. | Contraint `SupportTicket`. |
| `Priority` | enumeration | Priorite support. | Contraint `SupportTicket`. |
| `TicketStatus` | enumeration | Etat de traitement d'un ticket support. | Contraint `SupportTicket`. |
| `ModerationTargetType` | enumeration | Type de cible d'une action de moderation. | Contraint `ModerationAction`. |
| `ModerationActionType` | enumeration | Action de moderation executee. | Contraint `ModerationAction`. |

## 12. Interactions transversales majeures

1. Inscription et profil : `ApiGateway` securise la requete, `AuthService` cree `User`, `OtpCode` et `Session`, puis `ProfileService` cree un ou plusieurs `Profile`.
2. Validation conducteur/transporteur : `KycService` collecte `OfficialDocument`, journalise `DocumentAccessAudit`, cree `ValidationDecision` via `AdminProfile`, puis notifie l'utilisateur.
3. Recherche urbaine : `DriverProfile` publie `DriverPosition`, `GeoService` cherche les conducteurs proches, `PricingService` produit `FareEstimate`, puis `UrbanTrip` associe passager et mototaxi.
4. Interurbain : `TransporterProfile` ou `AgencyProfile` publie `IntercityAnnouncement`, `PassengerProfile` cree `Booking`, `BookingService` pose `SeatLock`, puis `PaymentService` confirme le paiement si necessaire.
5. Paiement : `PaymentService` cree une `PaymentTransaction`, poste des `LedgerEntry` dans les `Wallet`, genere un `Receipt` et notifie les parties.
6. Offline/NFC : `NfcPaymentService` emet `NfcPaymentToken`, `OfflineOperation` empile l'action, `OfflineSyncService` regularise le paiement et resout les `SyncConflict`.
7. Post-trajet : `TrackingService` cloture `Trip`, `RatingService` autorise `Rating`, `SupportService` gere les incidents, `AnalyticsService` consolide `KpiSnapshot`.

