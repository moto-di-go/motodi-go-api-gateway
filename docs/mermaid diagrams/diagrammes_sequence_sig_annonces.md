# Diagrammes de sequence - SIG, tarification et annonces MOTO'DI GO

Ces diagrammes couvrent les workflows demandes : partage GPS conducteur, recherche mototaxi proche, calcul itineraire + tarif standardise, publication d'annonce interurbaine et recherche/filtrage d'annonces.

## 6. Partage GPS conducteur

```mermaid
sequenceDiagram
    autonumber
    actor C as Conducteur
    participant M as Application mobile
    participant GPS as Capteur GPS
    participant G as ApiGateway
    participant Geo as GeoService
    participant PR as ProfileRepository
    participant POS as DriverPositionRepository
    participant RT as RealtimeBroker

    C->>M: Active disponibilite conducteur
    M->>GPS: Demander permission localisation
    alt Permission refusee
        GPS-->>M: Refus
        M-->>C: Affiche partage GPS impossible
    else Permission acceptee
        GPS-->>M: Coordonnees latitude/longitude
        loop Toutes les 5 secondes si conducteur disponible
            M->>G: POST /api/v1/drivers/position
            G->>G: Valider JWT + role conducteur
            G->>Geo: ingestDriverPosition(driverId, point)
            Geo->>PR: findValidatedDriverProfile(driverId)
            alt Profil non valide ou suspendu
                PR-->>Geo: Profil non autorise
                Geo-->>G: Erreur DRIVER_NOT_ALLOWED
                G-->>M: 403
                M-->>C: Stopper diffusion GPS
            else Profil valide
                PR-->>Geo: DriverProfile VALIDATED
                Geo->>Geo: verifier frequence + consentement
                Geo->>POS: save(DriverPosition)
                Geo->>RT: publish(driver.position.updated)
                Geo-->>G: Position acceptee
                G-->>M: 202 ACCEPTED
            end
        end
    end
```

## 7. Recherche mototaxi proche

```mermaid
sequenceDiagram
    autonumber
    actor P as Passager
    participant M as Application mobile
    participant G as ApiGateway
    participant Geo as GeoService
    participant Price as PricingService
    participant POS as DriverPositionRepository
    participant PR as ProfileRepository

    P->>M: Saisit origine/destination
    M->>G: GET /api/v1/urban/drivers/nearby
    G->>G: Valider JWT passager
    G->>Geo: findNearbyDrivers(origin, radiusKm)
    Geo->>POS: queryNear(origin, radiusKm, freshOnly=true)
    POS-->>Geo: Positions proches
    Geo->>PR: loadValidatedAvailableDrivers(driverIds)
    PR-->>Geo: Conducteurs valides et disponibles
    alt Aucun conducteur disponible
        Geo-->>G: Liste vide + raison
        G-->>M: 200 []
        M-->>P: Aucun mototaxi proche, proposer rayon plus large
    else Conducteurs trouves
        loop Pour chaque conducteur retenu
            Geo->>Geo: calculer distance d'approche
            Geo->>Price: estimateFare(origin, destination, context)
            Price-->>Geo: FareEstimate
        end
        Geo-->>G: Liste conducteurs + distance + prix estime
        G-->>M: 200 resultats tries par proximite
        M-->>P: Affiche mototaxis proches et prix
    end
```

## 8. Calcul itineraire + tarif standardise

```mermaid
sequenceDiagram
    autonumber
    actor P as Passager
    participant M as Application mobile
    participant G as ApiGateway
    participant Price as PricingService
    participant Geo as GeoService
    participant GIS as PostGIS
    participant TR as TrafficLayerRepository
    participant RR as PricingRuleRepository

    P->>M: Choisit origine et destination
    M->>G: POST /api/v1/pricing/estimate
    G->>G: Valider JWT
    G->>Price: estimateFare(origin, destination, context)
    Price->>Geo: buildRoute(origin, destination)
    Geo->>GIS: calcul distance, duree, polyline
    GIS-->>Geo: Route
    Geo->>TR: findActiveLayers(route)
    TR-->>Geo: Travaux, congestion, routes degradees
    alt Route principale bloquee
        Geo->>GIS: calculer route alternative
        GIS-->>Geo: RouteAlternative
    end
    Geo-->>Price: Route + alternatives + contexte trafic
    Price->>RR: findActiveRules(zone, timeWindow)
    RR-->>Price: PricingRule
    Price->>Price: appliquer base, distance, nuit, meteo, arrondi
    Price-->>G: FareEstimate avec trace regles appliquees
    G-->>M: 200 estimation tarifaire
    M-->>P: Affiche distance, duree, prix, regles
```

## 9. Publication d'annonce interurbaine

```mermaid
sequenceDiagram
    autonumber
    actor T as Transporteur / Agence
    participant M as Application mobile ou dashboard
    participant G as ApiGateway
    participant I as IntercityService
    participant PR as ProfileRepository
    participant VR as VehicleRepository
    participant AR as AnnouncementRepository
    participant N as NotificationService

    T->>M: Remplit ville depart, destination, date, prix, places
    M->>G: POST /api/v1/intercity/announcements
    G->>G: Valider JWT + role transporteur/agence
    G->>I: createAnnouncement(profileId, data)
    I->>PR: findById(profileId)
    PR-->>I: Profile
    I->>VR: findValidatedVehicle(vehicleId)
    VR-->>I: Vehicle
    I->>I: verifier profil valide, vehicule valide, places, horaire futur
    alt Profil/vehicule non valide ou donnees incoherentes
        I-->>G: Erreur ANNOUNCEMENT_INVALID
        G-->>M: 400/403
        M-->>T: Affiche erreurs de formulaire
    else Annonce valide
        I->>AR: save(IntercityAnnouncement status=PUBLISHED)
        I->>N: notifyFollowersOrInterestedUsers(announcement)
        N-->>I: Notifications programmees
        I-->>G: Annonce publiee
        G-->>M: 201 CREATED
        M-->>T: Affiche annonce publiee
    end
```

## 10. Recherche et filtrage d'annonces

```mermaid
sequenceDiagram
    autonumber
    actor V as Voyageur
    participant M as Application mobile
    participant G as ApiGateway
    participant I as IntercityService
    participant AR as AnnouncementRepository
    participant PR as ProfileRepository
    participant AI as AiRecommendationService

    V->>M: Saisit ville, date, budget, quartier, horaire
    M->>G: GET /api/v1/intercity/announcements?filters
    G->>G: Valider JWT si endpoint prive
    G->>I: searchAnnouncements(criteria)
    I->>I: normaliser AnnouncementSearchCriteria
    I->>AR: searchByCriteria(criteria, onlyAvailable=true)
    AR-->>I: Annonces candidates
    I->>PR: loadValidatedTransporters(profileIds)
    PR-->>I: Transporteurs valides
    I->>I: filtrer annonces non conformes + trier prix/horaire/proximite
    alt Aucun resultat
        I-->>G: Liste vide + suggestions elargissement
        G-->>M: 200 []
        M-->>V: Propose autre date, quartier ou budget
    else Resultats disponibles
        opt Assistant IA active
            I->>AI: recommend(criteria + annonces candidates)
            AI-->>I: Suggestions scorees + raisons
        end
        I-->>G: Liste annonces + places restantes + recommandations
        G-->>M: 200 resultats pagines
        M-->>V: Affiche filtres, tri et details annonces
    end
```

