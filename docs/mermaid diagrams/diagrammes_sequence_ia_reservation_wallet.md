# Diagrammes de sequence - IA, reservation et wallet MOTO'DI GO

Ces diagrammes couvrent les workflows demandes : recommandation IA de covoiturage, souscription a une annonce, verrouillage de place, recharge wallet Mobile Money et callback Mobile Money.

## 11. Recommandation IA de covoiturage

```mermaid
sequenceDiagram
    autonumber
    actor V as Voyageur
    participant M as Application mobile
    participant G as ApiGateway
    participant I as IntercityService
    participant AI as AiRecommendationService
    participant AR as AnnouncementRepository
    participant BR as BookingRepository
    participant MET as AiMetricsRepository

    V->>M: Demande recommandations covoiturage
    M->>G: POST /api/v1/ai/recommendations
    G->>G: Valider JWT + rate limit
    G->>AI: recommend(request)
    AI->>I: rechercher annonces existantes compatibles
    I->>AR: searchByCriteria(ville, quartier, horaire, places)
    AR-->>I: Annonces candidates
    I-->>AI: Liste annonces
    AI->>BR: loadUserHistory(anonymizedUserId)
    BR-->>AI: Historique anonymise
    AI->>AI: calculer score, contraintes, explication
    alt Donnees insuffisantes ou modele indisponible
        AI->>I: fallbackSearch(criteria)
        I-->>AI: Resultats deterministes
        AI->>MET: logMetric(fallback=true, latency)
        AI-->>G: Suggestions fallback
    else Recommandations IA disponibles
        AI->>MET: logMetric(fallback=false, latency)
        AI-->>G: Suggestions scorees + raisons
    end
    G-->>M: 200 recommandations
    M-->>V: Affiche score, raison et lien detail annonce
```

## 12. Souscription a une annonce

```mermaid
sequenceDiagram
    autonumber
    actor V as Voyageur
    participant M as Application mobile
    participant G as ApiGateway
    participant B as BookingService
    participant AR as AnnouncementRepository
    participant PR as ProfileRepository
    participant N as NotificationService

    V->>M: Clique Souscrire a l'annonce
    M->>G: POST /api/v1/intercity/announcements/{id}/bookings
    G->>G: Valider JWT + role passager
    G->>B: requestBooking(passengerId, announcementId, seats)
    B->>PR: findPassengerProfile(passengerId)
    PR-->>B: PassengerProfile
    B->>AR: findPublishedAnnouncement(announcementId)
    AR-->>B: IntercityAnnouncement
    B->>B: verifier statut annonce, date, places, doublon
    alt Annonce complete ou non disponible
        B-->>G: Erreur BOOKING_NOT_AVAILABLE
        G-->>M: 409
        M-->>V: Affiche raison indisponibilite
    else Souscription possible
        B->>B: creer Booking status=PENDING
        B->>N: sendBookingNotification(transporteur, SUBSCRIPTION)
        N-->>B: Notification programmee
        B-->>G: Booking PENDING
        G-->>M: 201 CREATED
        M-->>V: Affiche demande en attente/confirmation
    end
```

## 13. Verrouillage de place

```mermaid
sequenceDiagram
    autonumber
    participant B as BookingService
    participant TX as TransactionManager
    participant AR as AnnouncementRepository
    participant SL as SeatLockRepository
    participant BR as BookingRepository
    participant N as NotificationService

    B->>TX: demarrer transaction
    TX->>AR: lockAnnouncementForUpdate(announcementId)
    AR-->>TX: Annonce verrouillee
    TX->>AR: getRemainingSeats()
    alt Places insuffisantes
        TX-->>B: rollback
        B-->>B: lever NO_SEAT_AVAILABLE
    else Places disponibles
        TX->>SL: save(SeatLock seatsLocked, expiresAt)
        TX->>AR: decrementRemainingSeats(seats)
        TX->>BR: updateBooking(status=CONFIRMED ou PENDING_PAYMENT)
        TX-->>B: commit
        B->>N: notifyPassengerAndTransporter(CONFIRMATION)
        N-->>B: Notifications envoyees
    end

    Note over TX,AR: Le verrou pessimiste evite que deux reservations concurrentes consomment la meme place.
```

## 14. Recharge wallet Mobile Money

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant M as Application mobile
    participant G as ApiGateway
    participant P as PaymentService
    participant W as WalletService
    participant WR as WalletRepository
    participant TR as PaymentTransactionRepository
    participant MM as Fournisseur Mobile Money

    U->>M: Saisit montant + operateur
    M->>G: POST /api/v1/wallet/deposits
    G->>G: Valider JWT + rate limit paiement
    G->>P: initiateDeposit(walletId, provider, amount)
    P->>W: getWallet(walletId)
    W->>WR: findActiveWallet(walletId)
    WR-->>W: Wallet ACTIVE
    P->>TR: save(MobileMoneyDeposit status=INITIATED)
    P->>MM: initier paiement(amount, payerPhone, callbackUrl)
    alt Fournisseur refuse l'initialisation
        MM-->>P: Erreur operateur
        P->>TR: update status=FAILED
        P-->>G: Erreur DEPOSIT_INIT_FAILED
        G-->>M: 502
        M-->>U: Recharge impossible, ressayer
    else Demande acceptee
        MM-->>P: externalReference + statut PENDING
        P->>TR: update status=PENDING_CALLBACK
        P-->>G: Depot en attente confirmation
        G-->>M: 202 ACCEPTED
        M-->>U: Affiche instruction validation Mobile Money
    end
```

## 15. Callback Mobile Money

```mermaid
sequenceDiagram
    autonumber
    participant MM as Fournisseur Mobile Money
    participant G as ApiGateway
    participant P as PaymentService
    participant TR as PaymentTransactionRepository
    participant W as WalletService
    participant LED as LedgerRepository
    participant N as NotificationService

    MM->>G: POST /api/v1/integrations/mobile-money/callback
    G->>G: verifier signature + origine + idempotencyKey
    alt Signature invalide ou callback rejoue
        G-->>MM: 401/409 callback refuse
    else Callback valide
        G->>P: handleCallback(provider, payload)
        P->>TR: findByExternalReference(reference)
        TR-->>P: MobileMoneyDeposit
        alt Transaction deja confirmee
            P-->>G: OK idempotent
            G-->>MM: 200 ACK
        else Statut = SUCCESS
            P->>W: credit(walletId, amount, reason)
            W->>LED: save(LedgerEntry CREDIT)
            W-->>P: Wallet credite
            P->>TR: update status=CONFIRMED
            P->>N: sendPaymentAlert(user, depot confirme)
            N-->>P: Notification envoyee
            P-->>G: Callback traite
            G-->>MM: 200 ACK
        else Statut = FAILED ou EXPIRED
            P->>TR: update status=FAILED/EXPIRED
            P->>N: sendPaymentAlert(user, depot echoue)
            P-->>G: Callback traite sans credit
            G-->>MM: 200 ACK
        end
    end
```

