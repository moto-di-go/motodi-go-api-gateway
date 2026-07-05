# Diagramme de composants - MOTO'DI GO

Ce diagramme presente la structure modulaire de MOTO'DI GO : chaque composant encapsule une responsabilite, expose ou consomme des interfaces, et communique avec les autres modules via des contrats explicites.

## Principes retenus

- Un composant UML represente une partie modulaire et substituable du systeme, decrite par ses interfaces fournies et requises.
- Les relations ci-dessous montrent des dependances de service, pas des appels de methode internes.
- La notation utilise PlantUML, qui supporte les composants, interfaces, packages, bases de donnees et clouds externes.
- Le backend-core est volontairement detaille en sous-composants fonctionnels, car le backlog le decompose en services metier coherents.

```plantuml
@startuml
title Diagramme de composants - MOTO'DI GO
left to right direction
skinparam componentStyle uml2
skinparam packageStyle rectangle
skinparam shadowing false

skinparam component {
  BackgroundColor #F8FAFC
  BorderColor #334155
}
skinparam database {
  BackgroundColor #ECFDF5
  BorderColor #15803D
}
skinparam cloud {
  BackgroundColor #EFF6FF
  BorderColor #2563EB
}

actor "Passager" as Passenger
actor "Mototaxi" as MotoDriver
actor "Transporteur / Agence" as Carrier
actor "Administrateur" as AdminUser

package "Clients" {
  [Application Mobile Flutter] as Mobile <<mobile>>
  [Dashboard Web Admin] as AdminWeb <<web>>
}

package "API Edge" {
  [API Gateway Spring Cloud] as Gateway <<gateway>>
  interface "REST /api/v1" as IRest
  interface "JWT + RBAC" as IJwt
  interface "Temps reel\nWebSocket/SSE/Polling" as IRealtime
  interface "Callbacks externes\nMobile Money" as ICallbacks
}

package "Backend Core - Spring Boot" {
  [AuthService] as Auth <<service>>
  [ProfileService] as Profile <<service>>
  [KycService] as Kyc <<service>>
  [GeoService] as Geo <<service>>
  [PricingService] as Pricing <<service>>
  [IntercityService] as Intercity <<service>>
  [BookingService] as Booking <<service>>
  [WalletService] as Wallet <<service>>
  [PaymentService] as Payment <<service>>
  [NfcPaymentService] as Nfc <<service>>
  [NotificationService] as Notification <<service>>
  [TrackingService] as Tracking <<service>>
  [OfflineSyncService] as OfflineSync <<service>>
  [RatingService] as Rating <<service>>
  [SupportService] as Support <<service>>
  [AnalyticsService] as Analytics <<service>>

  interface "Auth API" as IAuth
  interface "Profil / RBAC API" as IProfile
  interface "KYC API" as IKyc
  interface "SIG API" as IGeo
  interface "Tarification API" as IPricing
  interface "Annonces API" as IIntercity
  interface "Reservation API" as IBooking
  interface "Wallet API" as IWallet
  interface "Paiement API" as IPayment
  interface "NFC Offline API" as INfc
  interface "Notification API" as INotification
  interface "Suivi API" as ITracking
  interface "Sync Offline API" as IOffline
  interface "Avis API" as IRating
  interface "Support API" as ISupport
  interface "KPI API" as IAnalytics
}

package "Backend IA - FastAPI" {
  [AiRecommendationService] as AI <<ai>>
  [AiMetricsAdapter] as AIMetrics <<ai>>
  interface "Recommendation API" as IAI
  interface "AI Metrics API" as IAIMetrics
}

database "PostgreSQL + PostGIS" as Postgres {
  [Schema metier] as BusinessSchema
  [Index spatiaux PostGIS] as SpatialIndexes
  [Ledger wallet] as LedgerSchema
  [Audit & KPI] as AuditSchema
}

database "Stockage objet chiffre" as ObjectStorage {
  [Documents KYC AES-256] as KycFiles
  [Recus PDF] as Receipts
}

database "Cache mobile local" as MobileCache {
  [Cartes offline] as OfflineMaps
  [File operations offline] as OfflineQueue
  [Jetons NFC signes] as NfcTokens
}

cloud "Fournisseurs externes" {
  [MTN MoMo API] as MTN
  [Orange Money API] as Orange
  [Agregateur paiement] as Aggregator
  [FCM / APNs] as Push
  [Mapbox / OpenStreetMap] as Maps
}

package "DevOps & Observabilite" {
  [Docker Compose] as Docker
  [GitHub Actions CI/CD] as CICD
  [VPS Ubuntu Production] as VPS
  [Logs / Metrics / Healthchecks] as Observability
}

Passenger --> Mobile
MotoDriver --> Mobile
Carrier --> Mobile
AdminUser --> AdminWeb

Mobile --> IRest : HTTPS JSON
Mobile --> IRealtime : suivi GPS/statuts
Mobile --> MobileCache : lecture/ecriture offline
AdminWeb --> IRest : HTTPS admin

IRest - Gateway
IJwt - Gateway
IRealtime - Gateway
ICallbacks - Gateway

Gateway --> IAuth : login, OTP, sessions
Gateway --> IProfile : profils, permissions
Gateway --> IKyc : documents, validation
Gateway --> IGeo : positions, recherche proximite
Gateway --> IPricing : itineraire, tarif
Gateway --> IIntercity : annonces, recherche
Gateway --> IBooking : reservation, verrouillage
Gateway --> IWallet : solde, ledger
Gateway --> IPayment : depot, paiement trajet
Gateway --> INfc : jetons offline
Gateway --> INotification : preferences, historique
Gateway --> ITracking : statuts course
Gateway --> IOffline : sync operations
Gateway --> IRating : notation
Gateway --> ISupport : tickets
Gateway --> IAnalytics : KPI
Gateway --> IAI : recommandations
Gateway --> IAIMetrics : metriques IA

IAuth - Auth
IProfile - Profile
IKyc - Kyc
IGeo - Geo
IPricing - Pricing
IIntercity - Intercity
IBooking - Booking
IWallet - Wallet
IPayment - Payment
INfc - Nfc
INotification - Notification
ITracking - Tracking
IOffline - OfflineSync
IRating - Rating
ISupport - Support
IAnalytics - Analytics
IAI - AI
IAIMetrics - AIMetrics

Auth --> Postgres : users, sessions, otp
Profile --> Postgres : profils, roles, permissions
Kyc --> Postgres : metadonnees KYC
Kyc --> ObjectStorage : pieces chiffrees
Geo --> Postgres : positions GPS PostGIS
Geo --> Maps : couches cartographiques
Pricing --> Postgres : regles tarifaires
Pricing --> Geo : distance, itineraire
Intercity --> Postgres : annonces, vehicules
Booking --> Postgres : reservations, locks
Booking --> Intercity : places restantes
Booking --> Payment : paiement requis
Wallet --> Postgres : wallets, ledger
Payment --> Wallet : debit/credit atomique
Payment --> MTN : depot MoMo
Payment --> Orange : depot Orange Money
Payment --> Aggregator : fallback paiement
Payment --> ObjectStorage : recus PDF
Nfc --> Wallet : reservation montant
Nfc --> Postgres : jetons, statut sync
Notification --> Push : push mobile
Notification --> Postgres : historique notifications
Tracking --> Geo : position conducteur
Tracking --> Booking : statut trajet
Tracking --> Notification : changements de statut
OfflineSync --> Booking : rejouer reservations
OfflineSync --> Nfc : regulariser jetons
OfflineSync --> Postgres : audit operations differees
Rating --> Postgres : avis, score moyen
Support --> Postgres : tickets, moderation
Analytics --> Postgres : agregations KPI
Analytics --> AIMetrics : taux usage IA

AI --> Intercity : annonces disponibles
AI --> Booking : historique anonymise
AI --> Analytics : evenement usage IA
AIMetrics --> Postgres : metriques agregees

Mobile --> Maps : rendu carte mobile
Mobile --> Push : reception push
Mobile --> NfcTokens : lecture/ecriture NFC
OfflineQueue --> IOffline : synchronisation differree

Docker --> Auth
Docker --> Gateway
Docker --> AI
Docker --> AdminWeb
CICD --> VPS : deploiement
VPS --> Gateway
VPS --> Postgres
VPS --> ObjectStorage
Observability --> Gateway
Observability --> Auth
Observability --> Payment
Observability --> AI

note bottom of Gateway
Point d'entree unique :
- routage /api/v1
- JWT/RBAC
- rate limiting
- correlation-id
- callbacks externes
end note

note bottom of Postgres
PostGIS porte les donnees spatiales :
positions, points, traces, index de proximite.
end note

note bottom of Payment
Le paiement reste isole :
idempotence, callbacks signes,
ledger et reconciliation admin.
end note
@enduml
```

## Lecture rapide du diagramme

- **Clients** : l'application Flutter sert les passagers, mototaxis, transporteurs et agences ; le dashboard web sert l'administration.
- **API Gateway** : centralise les routes, la securite JWT/RBAC, le temps reel et les callbacks externes.
- **Backend Core** : contient les services metier principaux : identite, KYC, SIG, tarification, annonces, reservation, wallet, paiement, offline, support et analytics.
- **Backend IA** : isole l'assistant de recommandation de covoiturage et ses metriques.
- **Donnees** : PostgreSQL/PostGIS porte les donnees metier et geospatiales ; le stockage objet conserve les documents KYC et recus ; le cache mobile garde les cartes et operations offline.
- **Externes** : Mobile Money, push notifications et cartographie sont des dependances externes explicites.
- **DevOps** : Docker, CI/CD, VPS et observabilite sont modelises comme composants de support d'exploitation.

## Sources de notation

- [OMG UML 2.5.1](https://www.omg.org/spec/UML/2.5.1/) : specification formelle UML, langage de modelisation pour visualiser, specifier, construire et documenter des systemes distribues.
- [PlantUML - Component Diagram](https://plantuml.com/component-diagram) : syntaxe officielle des composants, interfaces, packages, bases de donnees, clouds, ports et relations.

