# Diagrammes de sequence - Suivi, post-trajet et support MOTO'DI GO

Ces diagrammes couvrent les workflows demandes : suivi de trajet en temps reel, changement de statut de course, fin de trajet, notation post-trajet et creation de ticket support.

## 21. Suivi de trajet en temps reel

```mermaid
sequenceDiagram
    autonumber
    actor P as Passager
    actor C as Conducteur
    participant MP as Mobile passager
    participant MC as Mobile conducteur
    participant G as ApiGateway
    participant RT as RealtimeGateway
    participant T as TrackingService
    participant Geo as GeoService
    participant POS as DriverPositionRepository
    participant TR as TripRepository

    P->>MP: Ouvre trajet actif
    MP->>G: GET /api/v1/trips/active
    G->>T: getActiveTripForPassenger(passengerId)
    T->>TR: findActiveByPassenger(passengerId)
    TR-->>T: Trip actif
    T-->>G: Trip + dernier statut
    G-->>MP: 200 Trip actif

    MP->>RT: Subscribe trip.{tripId}
    RT->>G: Valider JWT et droit d'acces au trajet
    G-->>RT: Autorise subscription

    loop Pendant le trajet
        C->>MC: Position GPS capturee
        MC->>G: POST /api/v1/drivers/position
        G->>Geo: ingestDriverPosition(driverId, point)
        Geo->>POS: save(DriverPosition)
        Geo->>T: attachDriverPosition(tripId, positionId)
        T->>RT: publish trip.position.updated
        RT-->>MP: Position conducteur actualisee
        MP-->>P: Met a jour carte en temps reel
    end
```

## 22. Changement de statut de course

```mermaid
sequenceDiagram
    autonumber
    actor C as Conducteur
    participant MC as Mobile conducteur
    participant G as ApiGateway
    participant T as TrackingService
    participant TR as TripRepository
    participant EV as TripEventRepository
    participant N as NotificationService
    participant RT as RealtimeGateway

    C->>MC: Change statut course
    MC->>G: PATCH /api/v1/trips/{id}/status
    G->>G: Valider JWT + conducteur affecte au trajet
    G->>T: changeTripStatus(tripId, newStatus)
    T->>TR: findById(tripId)
    TR-->>T: Trip courant
    T->>T: verifier transition autorisee
    alt Transition invalide
        T-->>G: Erreur INVALID_TRIP_TRANSITION
        G-->>MC: 409
        MC-->>C: Statut refuse
    else Transition valide
        T->>TR: updateStatus(newStatus)
        T->>EV: save(TripEvent)
        T->>N: sendTripStatusNotification(passenger, newStatus)
        N-->>T: Notification envoyee/programmee
        T->>RT: publish trip.status.updated
        T-->>G: TripEvent cree
        G-->>MC: 200 OK
        MC-->>C: Statut mis a jour
    end
```

## 23. Fin de trajet

```mermaid
sequenceDiagram
    autonumber
    actor C as Conducteur
    participant MC as Mobile conducteur
    participant G as ApiGateway
    participant T as TrackingService
    participant TR as TripRepository
    participant EV as TripEventRepository
    participant Pay as PaymentService
    participant R as ReceiptService
    participant N as NotificationService
    participant RT as RealtimeGateway

    C->>MC: Termine le trajet
    MC->>G: POST /api/v1/trips/{id}/complete
    G->>G: Valider JWT + role conducteur
    G->>T: closeTrip(tripId)
    T->>TR: findActiveTrip(tripId)
    TR-->>T: Trip status=ON_BOARD
    alt Trajet non cloturable
        T-->>G: Erreur TRIP_NOT_COMPLETABLE
        G-->>MC: 409
        MC-->>C: Fin de trajet refusee
    else Trajet cloturable
        T->>TR: updateStatus(COMPLETED)
        T->>EV: save(TripEvent COMPLETED)
        opt Paiement final requis
            T->>Pay: finalizeTripPayment(tripId)
            Pay-->>T: RidePayment CONFIRMED
            T->>R: generateReceipt(payment, trip)
            R-->>T: Receipt
        end
        T->>N: notifyTripCompleted(passenger, driver)
        N-->>T: Notifications envoyees
        T->>RT: publish trip.completed
        T-->>G: Trip COMPLETED
        G-->>MC: 200 OK
        MC-->>C: Trajet termine
    end
```

## 24. Notation post-trajet

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant M as Application mobile
    participant G as ApiGateway
    participant R as RatingService
    participant TR as TripRepository
    participant RR as RatingRepository
    participant PR as ProfileRepository
    participant MOD as ModerationQueue

    U->>M: Saisit note et commentaire
    M->>G: POST /api/v1/trips/{id}/ratings
    G->>G: Valider JWT + participant du trajet
    G->>R: submitRating(tripId, raterId, ratedProfileId, stars, comment)
    R->>TR: findCompletedTripForUser(tripId, raterId)
    alt Trajet non termine ou utilisateur non participant
        TR-->>R: aucun trajet autorise
        R-->>G: Erreur RATING_NOT_ALLOWED
        G-->>M: 403/409
        M-->>U: Notation indisponible
    else Notation autorisee
        TR-->>R: Trip COMPLETED
        R->>RR: existsByTripAndRater(tripId, raterId)
        alt Deja note
            RR-->>R: true
            R-->>G: Erreur RATING_ALREADY_EXISTS
            G-->>M: 409
        else Premiere notation
            RR-->>R: false
            R->>RR: save(Rating status=PUBLISHED)
            R->>PR: recalculateAverage(ratedProfileId)
            opt Commentaire suspect
                R->>MOD: enqueueForModeration(ratingId)
            end
            R-->>G: Rating cree + nouveau score
            G-->>M: 201 CREATED
            M-->>U: Merci pour votre avis
        end
    end
```

## 25. Creation ticket support

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant M as Application mobile
    participant G as ApiGateway
    participant S as SupportService
    participant SR as SupportTicketRepository
    participant N as NotificationService
    participant ADM as Dashboard support

    U->>M: Remplit categorie, priorite, message
    M->>G: POST /api/v1/support/tickets
    G->>G: Valider JWT + anti-spam
    G->>S: createTicket(userId, category, message)
    S->>S: verifier categorie, priorite, contenu
    alt Donnees invalides ou spam
        S-->>G: Erreur SUPPORT_TICKET_INVALID
        G-->>M: 400/429
        M-->>U: Demande correction ou attente
    else Ticket valide
        S->>SR: save(SupportTicket status=OPEN)
        SR-->>S: Ticket cree
        S->>N: sendSupportAcknowledgement(user)
        N-->>S: Accuse envoye
        S->>ADM: notifyNewSupportTicket(ticket)
        ADM-->>S: Ticket visible equipe support
        S-->>G: SupportTicket
        G-->>M: 201 CREATED
        M-->>U: Affiche numero de ticket
    end
```

