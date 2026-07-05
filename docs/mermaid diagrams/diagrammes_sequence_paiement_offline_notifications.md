# Diagrammes de sequence - Paiement, offline et notifications MOTO'DI GO

Ces diagrammes couvrent les workflows demandes : paiement d'un trajet via wallet, generation du recu, paiement NFC hors-ligne, synchronisation des actions offline et notifications reservation/paiement/statut.

## 16. Paiement d'un trajet via wallet

```mermaid
sequenceDiagram
    autonumber
    actor P as Passager
    participant M as Application mobile
    participant G as ApiGateway
    participant Pay as PaymentService
    participant B as BookingService
    participant W as WalletService
    participant WR as WalletRepository
    participant LED as LedgerRepository
    participant TR as PaymentTransactionRepository
    participant N as NotificationService

    P->>M: Clique Payer le trajet
    M->>G: POST /api/v1/bookings/{id}/pay
    G->>G: Valider JWT + role passager
    G->>Pay: payTrip(bookingId, passengerWalletId)
    Pay->>B: getPayableBooking(bookingId, passengerId)
    B-->>Pay: Booking + montant + transporteur
    Pay->>W: getWallet(passengerWalletId)
    W->>WR: findActiveWallet(passengerWalletId)
    WR-->>W: Wallet passager
    Pay->>W: checkBalance(amount)
    alt Solde insuffisant
        W-->>Pay: Solde insuffisant
        Pay-->>G: Erreur INSUFFICIENT_BALANCE
        G-->>M: 402
        M-->>P: Propose recharge wallet
    else Solde suffisant
        Pay->>TR: save(RidePayment status=INITIATED)
        Pay->>W: debit(passengerWallet, amount)
        W->>LED: save(LedgerEntry DEBIT)
        Pay->>W: credit(transporterWallet, transporterAmount)
        W->>LED: save(LedgerEntry CREDIT)
        Pay->>TR: update status=CONFIRMED
        Pay->>B: markPaid(bookingId)
        B-->>Pay: Booking PAID
        Pay->>N: sendPaymentAlert(passager, transporteur)
        N-->>Pay: Notifications envoyees
        Pay-->>G: RidePayment confirme
        G-->>M: 200 paiement confirme
        M-->>P: Affiche paiement reussi
    end
```

## 17. Generation du recu

```mermaid
sequenceDiagram
    autonumber
    participant Pay as PaymentService
    participant TR as PaymentTransactionRepository
    participant B as BookingRepository
    participant R as ReceiptService
    participant RR as ReceiptRepository
    participant OBJ as ObjectStorage
    participant N as NotificationService
    actor P as Passager

    Pay->>TR: findConfirmedPayment(paymentId)
    TR-->>Pay: RidePayment CONFIRMED
    Pay->>B: findBooking(payment.bookingId)
    B-->>Pay: Booking + trajet + passager
    Pay->>R: generateReceipt(payment, booking)
    R->>R: construire numero MDG-YYYYMMDD-XXXX
    R->>R: generer PDF recu
    R->>OBJ: put(receipt.pdf)
    OBJ-->>R: pdfUrl
    R->>RR: save(Receipt)
    RR-->>R: Receipt
    R-->>Pay: Receipt genere
    Pay->>N: sendPaymentAlert(passager, recu disponible)
    N-->>Pay: Notification envoyee
    Pay-->>P: Recu disponible dans l'application
```

## 18. Paiement NFC hors-ligne

```mermaid
sequenceDiagram
    autonumber
    actor P as Passager
    actor C as Conducteur
    participant MP as Mobile passager
    participant MC as Mobile conducteur
    participant NFC as Puce NFC
    participant NFS as NfcPaymentService
    participant W as WalletService
    participant LOCAL as Stockage local offline

    P->>MP: Demande paiement hors-ligne
    MP->>NFS: issueToken(walletId, amount)
    NFS->>W: verifier wallet + plafond offline
    alt Wallet non eligible ou plafond depasse
        W-->>NFS: Refus
        NFS-->>MP: Erreur NFC_NOT_ALLOWED
        MP-->>P: Paiement NFC indisponible
    else Jeton autorise
        W-->>NFS: OK
        NFS->>NFS: signer jeton, fixer expiration
        NFS-->>MP: NfcPaymentToken ISSUED
        MP->>NFC: ecrire jeton signe
        NFC->>MC: transmettre jeton au conducteur
        MC->>MC: verifier signature locale + expiration
        alt Jeton invalide ou expire
            MC-->>C: Paiement refuse
        else Jeton accepte localement
            MC->>LOCAL: enregistrer OfflineOperation NFC_PAYMENT
            MP->>LOCAL: marquer paiement pending sync
            MC-->>C: Paiement accepte en attente synchronisation
            MP-->>P: Transaction en attente de synchronisation
        end
    end
```

## 19. Synchronisation des actions offline

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant M as Application mobile
    participant G as ApiGateway
    participant O as OfflineSyncService
    participant OR as OfflineOperationRepository
    participant NFC as NfcPaymentService
    participant B as BookingService
    participant Pay as PaymentService
    participant CON as ConflictResolver

    U->>M: Retrouve la connexion
    M->>G: POST /api/v1/offline/sync batch
    G->>G: Valider JWT + idempotency keys
    G->>O: syncBatch(userId, operations)
    O->>OR: save(SyncBatch STARTED)
    loop Pour chaque OfflineOperation
        O->>OR: verifier operation deja traitee
        alt Operation deja synchronisee
            OR-->>O: ignorer comme idempotente
        else Operation NFC_PAYMENT
            O->>NFC: validateDeferredToken(token)
            alt Jeton valide
                NFC->>Pay: regularizeLedger(tokenId)
                Pay-->>NFC: PaymentTransaction CONFIRMED
                O->>OR: markSynced(operation)
            else Jeton rejete ou double depense
                NFC-->>O: conflit NFC
                O->>CON: createConflict(operation, reason)
                O->>OR: markConflict(operation)
            end
        else Operation BOOKING_CREATE/CANCEL
            O->>B: replayBookingOperation(operation)
            alt Reservation rejouee avec succes
                B-->>O: OK
                O->>OR: markSynced(operation)
            else Conflit metier
                B-->>O: conflit
                O->>CON: createConflict(operation, reason)
                O->>OR: markConflict(operation)
            end
        end
    end
    O->>OR: update(SyncBatch COMPLETED ou PARTIAL_SUCCESS)
    O-->>G: Rapport sync + conflits eventuels
    G-->>M: 200 resultat synchronisation
    M-->>U: Affiche actions synchronisees ou conflits a resoudre
```

## 20. Notifications reservation, paiement et statut

```mermaid
sequenceDiagram
    autonumber
    participant Source as Service emetteur
    participant N as NotificationService
    participant PREF as NotificationPreferenceRepository
    participant NR as NotificationRepository
    participant PUSH as FCM/APNs
    actor U as Utilisateur
    participant M as Application mobile

    Source->>N: sendNotification(userId, type, reference)
    N->>PREF: findByUserId(userId)
    PREF-->>N: Preferences utilisateur
    alt Notifications desactivees pour ce type
        N->>NR: save(Notification status=QUEUED/READ_ONLY)
        N-->>Source: Notification non poussee mais historisee
    else Notifications autorisees
        N->>NR: save(Notification status=QUEUED)
        N->>PUSH: send(deviceToken, title, body)
        alt Envoi push reussi
            PUSH-->>N: messageId
            N->>NR: update status=SENT
            N-->>Source: Notification envoyee
            PUSH-->>M: Push reservation/paiement/statut
            M-->>U: Affiche alerte
        else Envoi push echoue
            PUSH-->>N: erreur
            N->>NR: update status=FAILED
            N->>N: planifier retry
            N-->>Source: Notification en retry
        end
    end

    Note over Source,N: Source peut etre BookingService, PaymentService, TrackingService ou KycService.
```

