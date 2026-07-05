# Diagrammes de sequence - Admin, analytics et production MOTO'DI GO

Ces diagrammes couvrent les workflows demandes : moderation avis/support, dashboard admin operations, parametrage des regles tarifaires/SIG, calcul et export KPI, deploiement/healthcheck production.

## 26. Moderation avis/support

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin support
    participant W as Dashboard admin
    participant G as ApiGateway
    participant MOD as ModerationService
    participant RR as RatingRepository
    participant SR as SupportTicketRepository
    participant MR as ModerationActionRepository
    participant N as NotificationService

    A->>W: Ouvre file moderation
    W->>G: GET /api/v1/admin/moderation/queue
    G->>G: Valider JWT + RBAC support/admin
    G->>MOD: listModerationQueue(filters)
    MOD->>RR: findFlaggedRatings()
    MOD->>SR: findOpenOrEscalatedTickets()
    RR-->>MOD: Avis signales
    SR-->>MOD: Tickets support
    MOD-->>G: File moderation
    G-->>W: 200 OK
    W-->>A: Affiche avis/tickets a traiter

    A->>W: Applique action masquer/restaurer/escalader
    W->>G: POST /api/v1/admin/moderation/actions
    G->>MOD: applyAction(targetType, targetId, action, reason)
    alt Cible = Rating
        MOD->>RR: updateRatingStatus(targetId, action)
    else Cible = SupportTicket
        MOD->>SR: updateTicketStatus(targetId, action)
    end
    MOD->>MR: save(ModerationAction)
    MOD->>N: notifyUserIfRequired(targetOwner, action)
    N-->>MOD: Notification envoyee/programmee
    MOD-->>G: Action appliquee
    G-->>W: 200 OK
    W-->>A: File mise a jour
```

## 27. Dashboard admin operations

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin operations
    participant W as Dashboard admin
    participant G as ApiGateway
    participant OPS as AdminOpsService
    participant UR as UserRepository
    participant PR as ProfileRepository
    participant AR as AnnouncementRepository
    participant BR as BookingRepository
    participant TR as PaymentTransactionRepository

    A->>W: Ouvre tableau de bord operations
    W->>G: GET /api/v1/admin/ops/overview
    G->>G: Valider JWT + RBAC operations
    G->>OPS: getOverview(filters)
    par Utilisateurs et profils
        OPS->>UR: countUsersByStatus()
        OPS->>PR: countProfilesByValidationStatus()
    and Annonces et reservations
        OPS->>AR: countAnnouncementsByStatus()
        OPS->>BR: countBookingsByStatus()
    and Paiements
        OPS->>TR: summarizeTransactions(period)
    end
    UR-->>OPS: Totaux utilisateurs
    PR-->>OPS: Totaux profils
    AR-->>OPS: Totaux annonces
    BR-->>OPS: Totaux reservations
    TR-->>OPS: Volumes wallet/paiements
    OPS-->>G: Donnees agregees dashboard
    G-->>W: 200 OK
    W-->>A: Affiche cartes, tableaux, filtres

    A->>W: Filtre une liste operationnelle
    W->>G: GET /api/v1/admin/ops/bookings?status=PAID
    G->>OPS: searchBookings(filters)
    OPS->>BR: search(filters, pagination)
    BR-->>OPS: Page reservations
    OPS-->>G: Resultats masques selon permissions
    G-->>W: 200 OK
    W-->>A: Affiche liste detaillee
```

## 28. Parametrage regles tarifaires/SIG

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin SIG/tarif
    participant W as Dashboard admin
    participant G as ApiGateway
    participant Price as PricingService
    participant Geo as GeoService
    participant RR as PricingRuleRepository
    participant LR as TrafficLayerRepository
    participant AUD as AuditLogRepository

    A->>W: Cree/modifie regle tarifaire
    W->>G: POST /api/v1/admin/pricing/rules
    G->>G: Valider JWT + permission PRICING_WRITE
    G->>Price: createPricingRule(rule)
    Price->>Price: valider zone, creneau, coefficients, dates
    alt Regle invalide
        Price-->>G: Erreur PRICING_RULE_INVALID
        G-->>W: 400
        W-->>A: Affiche erreurs de saisie
    else Regle valide
        Price->>RR: save(PricingRule)
        Price->>AUD: save(auteur, avant/apres)
        Price-->>G: Regle active/enregistree
        G-->>W: 201 CREATED
    end

    A->>W: Publie couche SIG trafic/travaux/route degradee
    W->>G: POST /api/v1/admin/gis/layers
    G->>Geo: publishTrafficLayer(layer)
    Geo->>Geo: valider geometrie WKT/PostGIS + dates
    alt Geometrie invalide
        Geo-->>G: Erreur GIS_LAYER_INVALID
        G-->>W: 400
        W-->>A: Affiche geometrie incorrecte
    else Couche valide
        Geo->>LR: save(TrafficLayer active=true)
        Geo->>AUD: save(auteur, couche publiee)
        Geo-->>G: Couche publiee
        G-->>W: 201 CREATED
        W-->>A: Previsualisation carte mise a jour
    end
```

## 29. Calcul et export KPI

```mermaid
sequenceDiagram
    autonumber
    actor D as Decideur/Admin
    participant W as Dashboard admin
    participant G as ApiGateway
    participant AN as AnalyticsService
    participant UR as UserRepository
    participant DR as DriverPositionRepository
    participant BR as BookingRepository
    participant TR as PaymentTransactionRepository
    participant AIR as AiMetricsRepository
    participant CSV as CsvExporter

    D->>W: Selectionne periode KPI
    W->>G: GET /api/v1/admin/kpi?period
    G->>G: Valider JWT + permission KPI_READ
    G->>AN: computeKpi(period)
    par Adoption
        AN->>UR: countMonthlyActiveUsers(period)
    and Conducteurs verifies
        AN->>DR: countActiveVerifiedDrivers(period)
    and Reservations
        AN->>BR: countReservations(period)
    and Revenus/wallet
        AN->>TR: sumConfirmedTransactions(period)
    and IA
        AN->>AIR: computeAiUsage(period)
    end
    UR-->>AN: MAU
    DR-->>AN: conducteurs actifs
    BR-->>AN: reservations
    TR-->>AN: volume transactions
    AIR-->>AN: taux IA, latence, erreurs
    AN->>AN: anonymiser et agreger
    AN-->>G: KpiSnapshot
    G-->>W: 200 KPI
    W-->>D: Affiche graphiques et indicateurs

    D->>W: Clique Export CSV
    W->>G: GET /api/v1/admin/kpi/export.csv
    G->>AN: exportCsv(snapshotId)
    AN->>CSV: generate(KpiSnapshot)
    CSV-->>AN: fichier CSV
    AN-->>G: CSV
    G-->>W: 200 text/csv
    W-->>D: Telecharge export
```

## 30. Deploiement et healthcheck production

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Equipe technique
    participant GH as GitHub Actions
    participant REG as Container Registry
    participant VPS as Serveur VPS Ubuntu
    participant GW as ApiGateway
    participant CORE as Backend Core
    participant AI as Backend IA
    participant WEB as Dashboard admin
    participant DB as PostgreSQL/PostGIS

    Dev->>GH: Push tag release
    GH->>GH: build + tests + scans
    alt Tests ou scans echouent
        GH-->>Dev: Pipeline rouge, deploiement bloque
    else Pipeline valide
        GH->>REG: push images versionnees
        REG-->>GH: Images publiees
        GH->>VPS: deploiement staging/production
        VPS->>REG: pull images
        VPS->>DB: backup avant migration
        DB-->>VPS: backup OK
        VPS->>CORE: lancer migrations
        CORE->>DB: appliquer migrations
        DB-->>CORE: migrations OK
        VPS->>GW: restart gateway
        VPS->>CORE: restart backend-core
        VPS->>AI: restart backend-ai
        VPS->>WEB: restart dashboard

        par Healthchecks services
            VPS->>GW: GET /health
            VPS->>CORE: GET /health
            VPS->>AI: GET /health
            VPS->>WEB: GET /health
        and Healthcheck base
            CORE->>DB: SELECT 1 + PostGIS extension
        end

        alt Healthcheck echoue
            VPS-->>GH: Smoke tests KO
            GH->>VPS: rollback version precedente
            VPS-->>Dev: Deploiement annule + rollback
        else Healthchecks OK
            VPS-->>GH: Smoke tests OK
            GH-->>Dev: Release production validee
        end
    end
```

