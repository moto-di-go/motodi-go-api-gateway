# Diagrammes de sequence - Identite et KYC MOTO'DI GO

Ces diagrammes couvrent les workflows demandes : inscription + OTP, connexion/session, profils, upload KYC et validation/rejet administratif.

## 1. Inscription utilisateur + verification OTP

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur mobile
    participant M as Application mobile
    participant G as ApiGateway
    participant A as AuthService
    participant UR as UserRepository
    participant O as OtpService
    participant OR as OtpRepository
    participant SMS as Fournisseur SMS
    participant W as WalletService

    U->>M: Saisit telephone, mot de passe, CGU
    M->>G: POST /api/v1/auth/register
    G->>A: register(phone, password, cgu)
    A->>UR: existsByPhone(phone)
    alt Numero deja inscrit
        UR-->>A: true
        A-->>G: Erreur PHONE_ALREADY_EXISTS
        G-->>M: 409 conflit
        M-->>U: Affiche message clair
    else Numero disponible
        UR-->>A: false
        A->>UR: save(User status=PENDING_PHONE_VERIFICATION)
        A->>O: sendOtp(user, REGISTRATION)
        O->>OR: save(OtpCode expiresAt)
        O->>SMS: envoyer OTP au +237...
        SMS-->>O: accepte
        A-->>G: User cree + OTP envoye
        G-->>M: 201 CREATED
        M-->>U: Demande le code OTP
    end

    U->>M: Saisit code OTP
    M->>G: POST /api/v1/auth/otp/verify
    G->>A: verifyOtp(phone, code)
    A->>OR: findActiveOtp(phone, REGISTRATION)
    OR-->>A: OtpCode
    A->>O: verify(code)
    alt OTP invalide ou expire
        O-->>A: false
        A-->>G: Erreur OTP_INVALID
        G-->>M: 400
        M-->>U: Invite a ressayer ou regenerer
    else OTP valide
        O-->>A: true
        A->>OR: markConsumed(otp)
        A->>UR: activateUser(phone)
        A->>W: createWallet(userId)
        W-->>A: Wallet cree
        A-->>G: Compte ACTIVE
        G-->>M: 200 OK
        M-->>U: Redirection selection profil
    end
```

## 2. Connexion, refresh token et deconnexion

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur mobile
    participant M as Application mobile
    participant G as ApiGateway
    participant A as AuthService
    participant UR as UserRepository
    participant S as SessionRepository
    participant J as JwtProvider

    U->>M: Saisit telephone + mot de passe
    M->>G: POST /api/v1/auth/login
    G->>A: login(phone, password)
    A->>UR: findByPhone(phone)
    UR-->>A: User
    A->>A: verifier mot de passe + statut ACTIVE
    alt Identifiants invalides
        A-->>G: Erreur AUTH_INVALID
        G-->>M: 401
        M-->>U: Affiche erreur
    else Identifiants valides
        A->>J: generateAccessToken(user, roles)
        J-->>A: accessToken
        A->>J: generateRefreshToken(user)
        J-->>A: refreshToken
        A->>S: save(Session ACTIVE)
        A-->>G: tokens + expiration
        G-->>M: 200 OK
        M->>M: Stocke tokens securises
        M-->>U: Ouvre accueil
    end

    M->>G: POST /api/v1/auth/refresh
    G->>A: refresh(refreshToken)
    A->>S: findActiveByRefreshTokenHash()
    alt Refresh token expire/revoque
        S-->>A: aucune session active
        A-->>G: Erreur SESSION_EXPIRED
        G-->>M: 401
        M-->>U: Redirection login
    else Refresh valide
        S-->>A: Session ACTIVE
        A->>J: generateAccessToken(user, roles)
        A->>S: rotateRefreshToken(session)
        A-->>G: nouveaux tokens
        G-->>M: 200 OK
    end

    U->>M: Clique deconnexion
    M->>G: POST /api/v1/auth/logout
    G->>A: logout(sessionId)
    A->>S: revoke(sessionId)
    S-->>A: Session REVOKED
    A-->>G: OK
    G-->>M: 204 NO CONTENT
    M->>M: Supprime tokens locaux
    M-->>U: Retour ecran login
```

## 3. Creation et changement de profil actif

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant M as Application mobile
    participant G as ApiGateway
    participant P as ProfileService
    participant PR as ProfileRepository
    participant RBAC as PermissionService

    U->>M: Choisit type profil
    M->>G: POST /api/v1/profiles
    G->>G: Valider JWT
    G->>P: createProfile(userId, type, data)
    P->>PR: findByUserId(userId)
    PR-->>P: profils existants
    P->>P: verifier compatibilite des profils
    alt Profil incompatible ou donnees invalides
        P-->>G: Erreur PROFILE_RULE_VIOLATION
        G-->>M: 400
        M-->>U: Affiche correction attendue
    else Profil autorise
        P->>PR: save(Profile validationStatus=DRAFT/PENDING)
        P->>RBAC: computeAllowedActions(profile)
        RBAC-->>P: actions autorisees
        P-->>G: Profil cree + actions
        G-->>M: 201 CREATED
        M-->>U: Affiche profil et statut validation
    end

    U->>M: Selectionne un autre profil actif
    M->>G: PUT /api/v1/profiles/active/{profileId}
    G->>G: Valider JWT
    G->>P: switchActiveProfile(userId, profileId)
    P->>PR: findOwnedProfile(userId, profileId)
    alt Profil introuvable ou suspendu
        PR-->>P: aucun profil utilisable
        P-->>G: Erreur PROFILE_NOT_ALLOWED
        G-->>M: 403
        M-->>U: Profil indisponible
    else Profil valide
        PR-->>P: Profile
        P->>PR: markActive(profileId)
        P->>RBAC: computeAllowedActions(profile)
        P-->>G: Profil actif + permissions
        G-->>M: 200 OK
        M->>M: Met a jour navigation
        M-->>U: Affiche parcours du role actif
    end
```

## 4. Upload documents KYC

```mermaid
sequenceDiagram
    autonumber
    actor U as Conducteur / Transporteur
    participant M as Application mobile
    participant G as ApiGateway
    participant K as KycService
    participant P as ProfileRepository
    participant D as DocumentRepository
    participant AV as Antivirus/FileValidator
    participant ENC as EncryptionService
    participant OBJ as ObjectStorage

    U->>M: Selectionne CNI/permis/assurance/photo
    M->>G: POST /api/v1/kyc/documents multipart
    G->>G: Valider JWT + role conducteur/transporteur
    G->>K: uploadDocument(profileId, file, type)
    K->>P: findById(profileId)
    P-->>K: Profile
    K->>K: verifier document requis pour ProfileType
    K->>AV: verifier format, taille, malware
    alt Fichier invalide
        AV-->>K: rejet
        K-->>G: Erreur DOCUMENT_INVALID
        G-->>M: 400
        M-->>U: Affiche type/taille attendus
    else Fichier valide
        AV-->>K: OK
        K->>ENC: encrypt(file)
        ENC-->>K: fichier chiffre + checksum
        K->>OBJ: put(encryptedFile)
        OBJ-->>K: encryptedStorageKey
        K->>D: save(OfficialDocument status=UPLOADED/ENCRYPTED)
        K->>P: updateValidationStatus(PENDING si dossier complet)
        K-->>G: Document enregistre + statut dossier
        G-->>M: 201 CREATED
        M-->>U: Affiche document en attente validation
    end
```

## 5. Validation ou rejet KYC par admin

```mermaid
sequenceDiagram
    autonumber
    actor A as Administrateur
    participant W as Dashboard admin
    participant G as ApiGateway
    participant K as KycService
    participant P as ProfileRepository
    participant D as DocumentRepository
    participant AU as DocumentAccessAuditRepository
    participant VD as ValidationDecisionRepository
    participant N as NotificationService

    A->>W: Ouvre file KYC en attente
    W->>G: GET /api/v1/admin/kyc/pending
    G->>G: Valider JWT + RBAC admin
    G->>K: listPendingProfiles()
    K->>P: findByValidationStatus(PENDING)
    P-->>K: Profils + documents
    K-->>G: Dossiers KYC
    G-->>W: 200 OK
    W-->>A: Affiche dossiers

    A->>W: Consulte documents d'un profil
    W->>G: GET /api/v1/admin/kyc/{profileId}/documents
    G->>K: getDocumentsForReview(profileId, adminId)
    K->>D: findByProfile(profileId)
    K->>AU: save(DocumentAccessAudit action=VIEW)
    K-->>G: Documents consultables
    G-->>W: 200 OK

    A->>W: Valide ou rejette avec motif
    W->>G: POST /api/v1/admin/kyc/{profileId}/decision
    G->>G: Valider permission KYC_DECIDE
    G->>K: reviewProfile(profileId, decision, reason)
    K->>K: verifier motif obligatoire si REJECT
    alt Rejet sans motif
        K-->>G: Erreur REJECTION_REASON_REQUIRED
        G-->>W: 400
        W-->>A: Demande un motif
    else Decision valide
        K->>VD: save(ValidationDecision)
        alt Decision = VALIDATE
            K->>P: updateStatus(profileId, VALIDATED)
            K->>D: markAccepted(requiredDocuments)
            K->>N: sendKycDecision(profileOwner, VALIDATED)
        else Decision = REJECT
            K->>P: updateStatus(profileId, REJECTED)
            K->>D: markRejected(documents, reason)
            K->>N: sendKycDecision(profileOwner, REJECTED, reason)
        end
        N-->>K: Notification creee/envoyee
        K-->>G: Decision appliquee
        G-->>W: 200 OK
        W-->>A: Dossier mis a jour
    end
```

