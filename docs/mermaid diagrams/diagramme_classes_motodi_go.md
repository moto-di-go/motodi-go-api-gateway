# Diagramme de classes - MOTO'DI GO

Sources analysees : `Cahier_des_Charges_MOTO_DI_GO.docx`, `Cahier_des_Charges_MOTO_DI_GO.pdf`, `Product_Backlog_MOTO_DI_GO.html` et les tickets du dossier `github_tickets`.

Notes de lecture :

- `<|--` represente l'heritage UML.
- `*--` represente la composition forte.
- `o--` represente l'agregation.
- `..>` represente une dependance applicative ; les libelles `<<include>>` et `<<extend>>` signalent les enchainements de cas/metiers reutilises ou optionnels.
- Les attributs et operations sont deduits du cahier des charges, du backlog MGO-001 a MGO-034 et des tickets backend/mobile/admin/gateway/IA.

```mermaid
classDiagram
direction LR

class User {
  +UUID id
  +String phoneNumber
  +String passwordHash
  +String fullName
  +String email
  +String locale
  +Boolean cguAccepted
  +UserStatus status
  +DateTime phoneVerifiedAt
  +DateTime createdAt
  +DateTime updatedAt
  +register()
  +verifyPhone(otpCode)
  +changePassword(newPassword)
  +activate()
  +deactivate(reason)
}

class Session {
  +UUID id
  +String accessTokenHash
  +String refreshTokenHash
  +String deviceId
  +String ipAddress
  +DateTime issuedAt
  +DateTime expiresAt
  +DateTime revokedAt
  +SessionStatus status
  +refresh()
  +revoke()
  +isExpired() Boolean
}

class OtpCode {
  +UUID id
  +String destinationPhone
  +OtpPurpose purpose
  +String codeHash
  +Integer attempts
  +DateTime expiresAt
  +DateTime consumedAt
  +verify(code) Boolean
  +expire()
  +consume()
}

class Profile {
  <<abstract>>
  +UUID id
  +ProfileType type
  +ValidationStatus validationStatus
  +String displayName
  +String avatarUrl
  +Double averageRating
  +Boolean active
  +DateTime createdAt
  +update(data)
  +submitForValidation()
  +markValidated()
  +markRejected(reason)
  +canOperate() Boolean
}

class PassengerProfile {
  +String preferredLanguage
  +String emergencyContact
  +searchUrbanRide(origin, destination)
  +bookAnnouncement(announcementId)
  +payTrip(tripId)
}

class DriverProfile {
  <<abstract>>
  +DriverAvailability availability
  +Boolean gpsConsent
  +DateTime lastAvailabilityChangeAt
  +setAvailability(status)
  +sharePosition(point)
  +stopPositionSharing()
}

class MotoTaxiProfile {
  +String licenseNumber
  +String operatingZone
  +acceptUrbanRide(requestId)
  +startUrbanTrip(tripId)
}

class TransporterProfile {
  +String permitNumber
  +String companyName
  +publishAnnouncement(data)
  +confirmBooking(bookingId)
}

class AgencyProfile {
  +String legalName
  +String tradeRegisterNumber
  +String billingPlan
  +manageFleet()
  +viewAgencyStats(period)
}

class AdminProfile {
  +AdminRole adminRole
  +Set permissions
  +validateProfile(profileId, decision)
  +moderateContent(targetId, action)
  +exportCsv(scope)
}

Profile <|-- PassengerProfile
Profile <|-- DriverProfile
DriverProfile <|-- MotoTaxiProfile
DriverProfile <|-- TransporterProfile
Profile <|-- AgencyProfile
Profile <|-- AdminProfile
User "1" *-- "1..*" Profile : possede
User "1" *-- "0..*" Session : ouvre
User "1" *-- "0..*" OtpCode : recoit

class Vehicle {
  +UUID id
  +VehicleType type
  +String plateNumber
  +String brand
  +String model
  +Integer capacity
  +String color
  +ValidationStatus validationStatus
  +Boolean active
  +updateInfo(data)
  +markUnavailable()
}

class OfficialDocument {
  +UUID id
  +DocumentType type
  +DocumentStatus status
  +String encryptedStorageKey
  +String mimeType
  +Long sizeBytes
  +String checksum
  +String rejectionReason
  +DateTime uploadedAt
  +upload(file)
  +validateFormat()
  +encrypt()
  +markAccepted()
  +markRejected(reason)
}

class ValidationDecision {
  +UUID id
  +DecisionType decision
  +String reason
  +DateTime decidedAt
  +apply()
  +notifyUser()
}

class DocumentAccessAudit {
  +UUID id
  +AccessAction action
  +String actorRole
  +DateTime accessedAt
  +String correlationId
  +record()
}

DriverProfile "1" o-- "0..*" Vehicle : utilise
AgencyProfile "1" o-- "0..*" Vehicle : gere
Profile "1" *-- "0..*" OfficialDocument : fournit
Profile "1" *-- "0..*" ValidationDecision : historique
OfficialDocument "1" *-- "0..*" DocumentAccessAudit : audit
AdminProfile "1" --> "0..*" ValidationDecision : statue
AdminProfile "1" --> "0..*" DocumentAccessAudit : consulte

class GeoPoint {
  +Decimal latitude
  +Decimal longitude
  +String postgisGeometry
  +distanceTo(other) Double
}

class DriverPosition {
  +UUID id
  +GeoPoint point
  +Double speedKmh
  +Double accuracyMeters
  +DateTime capturedAt
  +DateTime receivedAt
  +Boolean consentConfirmed
  +isFresh(maxAgeSeconds) Boolean
}

class Route {
  +UUID id
  +GeoPoint origin
  +GeoPoint destination
  +String polyline
  +Double distanceKm
  +Integer durationMinutes
  +RouteStatus status
  +calculateDistance()
  +estimateDuration()
}

class RouteAlternative {
  +UUID id
  +String reason
  +String polyline
  +Double extraDistanceKm
  +Integer extraDurationMinutes
  +proposeWhenBlocked()
}

class TrafficLayer {
  +UUID id
  +TrafficLayerType type
  +String label
  +String geometry
  +Severity severity
  +DateTime validFrom
  +DateTime validTo
  +Boolean active
  +publish()
  +expire()
}

class PricingRule {
  +UUID id
  +String zoneCode
  +TimeWindow timeWindow
  +Decimal baseFare
  +Decimal pricePerKm
  +Decimal nightCoefficient
  +Decimal weatherCoefficient
  +Boolean active
  +DateTime effectiveFrom
  +DateTime effectiveTo
  +apply(route) FareEstimate
  +auditChange()
}

class FareEstimate {
  +UUID id
  +Decimal amountFcfa
  +Double distanceKm
  +Integer durationMinutes
  +String appliedRulesTrace
  +DateTime calculatedAt
  +recalculate()
  +isStillValid() Boolean
}

DriverProfile "1" *-- "0..*" DriverPosition : publie
Route "1" *-- "0..*" RouteAlternative : alternatives
Route "1" --> "0..*" TrafficLayer : evite
PricingRule "1" --> "0..*" FareEstimate : produit
FareEstimate "1" --> "1" Route : pour

class Trip {
  <<abstract>>
  +UUID id
  +TripType type
  +TripStatus status
  +GeoPoint origin
  +GeoPoint destination
  +DateTime plannedDepartureAt
  +DateTime startedAt
  +DateTime completedAt
  +Decimal finalPriceFcfa
  +start()
  +complete()
  +cancel(reason)
  +canTransitionTo(status) Boolean
}

class UrbanTrip {
  +Double driverApproachDistanceKm
  +requestDriver()
  +assignDriver(driverProfileId)
}

class IntercityTrip {
  +String departureCity
  +String destinationCity
  +String departureQuarter
  +Integer availableSeats
  +lockSeat()
}

class IntercityAnnouncement {
  +UUID id
  +String departureCity
  +String destinationCity
  +String departureQuarter
  +Date departureDate
  +Time departureTime
  +Decimal priceFcfa
  +Integer totalSeats
  +Integer remainingSeats
  +String instructions
  +AnnouncementStatus status
  +create()
  +update(data)
  +publish()
  +close()
  +hasSeatAvailable() Boolean
}

class AnnouncementSearchCriteria {
  +String departureCity
  +String destinationCity
  +Date date
  +Decimal maxPrice
  +String quarter
  +TimeRange timeRange
  +Boolean onlyAvailable
  +sortByPrice()
  +sortByDepartureTime()
}

class Booking {
  +UUID id
  +BookingStatus status
  +Integer seatsRequested
  +DateTime requestedAt
  +DateTime confirmedAt
  +DateTime cancelledAt
  +DateTime expiresAt
  +String cancellationReason
  +request()
  +confirm()
  +cancel(reason)
  +expire()
  +isPayable() Boolean
}

class SeatLock {
  +UUID id
  +Integer seatsLocked
  +DateTime lockedAt
  +DateTime expiresAt
  +Boolean consumed
  +acquire()
  +release()
  +consume()
}

class TripEvent {
  +UUID id
  +TripStatus status
  +String label
  +DateTime occurredAt
  +String source
  +record()
}

Trip <|-- UrbanTrip
Trip <|-- IntercityTrip
IntercityAnnouncement "1" --> "1" IntercityTrip : prepare
TransporterProfile "1" --> "0..*" IntercityAnnouncement : publie
AgencyProfile "1" --> "0..*" IntercityAnnouncement : publie
Vehicle "1" --> "0..*" IntercityAnnouncement : affecte
PassengerProfile "1" --> "0..*" Booking : effectue
IntercityAnnouncement "1" *-- "0..*" Booking : recoit
Booking "1" *-- "0..1" SeatLock : verrouille
Booking "0..*" --> "1" Trip : concerne
Trip "1" *-- "0..*" TripEvent : journalise
UrbanTrip "0..*" --> "1" PassengerProfile : passager
UrbanTrip "0..*" --> "1" MotoTaxiProfile : conducteur
IntercityTrip "0..*" --> "1" TransporterProfile : transporteur
Trip "1" --> "0..1" FareEstimate : tarif
AnnouncementSearchCriteria ..> IntercityAnnouncement : filtre

class Wallet {
  +UUID id
  +String currency
  +Decimal currentBalance
  +WalletStatus status
  +DateTime createdAt
  +credit(amount, reason)
  +debit(amount, reason)
  +availableBalance() Decimal
  +freeze(reason)
}

class LedgerEntry {
  +UUID id
  +LedgerEntryType type
  +Decimal amount
  +String currency
  +String reference
  +DateTime bookedAt
  +String idempotencyKey
  +post()
  +reverse(reason)
}

class PaymentTransaction {
  <<abstract>>
  +UUID id
  +PaymentType type
  +PaymentStatus status
  +Decimal amount
  +String currency
  +String externalReference
  +String idempotencyKey
  +DateTime initiatedAt
  +DateTime confirmedAt
  +initiate()
  +confirm()
  +fail(reason)
  +isIdempotentDuplicate(key) Boolean
}

class MobileMoneyDeposit {
  +PaymentProvider provider
  +String callbackSignature
  +String payerPhone
  +initiateDeposit()
  +handleSignedCallback(payload)
  +reconcile()
}

class RidePayment {
  +Decimal platformFee
  +Decimal transporterAmount
  +payBooking(bookingId)
  +splitFunds()
  +rollback()
}

class NfcPaymentToken {
  +UUID id
  +String signedToken
  +Decimal amount
  +DateTime issuedAt
  +DateTime expiresAt
  +DateTime consumedAt
  +NfcTokenStatus status
  +issue()
  +validateSignature()
  +consume()
  +preventReplay()
}

class Receipt {
  +UUID id
  +String receiptNumber
  +Decimal amount
  +String currency
  +DateTime issuedAt
  +String pdfUrl
  +generate()
  +download()
}

PaymentTransaction <|-- MobileMoneyDeposit
PaymentTransaction <|-- RidePayment
User "1" *-- "1" Wallet : detient
Wallet "1" *-- "0..*" LedgerEntry : ledger
PaymentTransaction "1" --> "1..*" LedgerEntry : comptabilise
MobileMoneyDeposit "0..*" --> "1" Wallet : credite
RidePayment "0..*" --> "1" Wallet : debitePassager
RidePayment "0..*" --> "1" Wallet : crediteTransporteur
Booking "0..1" --> "0..1" RidePayment : regleePar
Trip "0..1" --> "0..1" RidePayment : paiementFinal
PaymentTransaction "1" *-- "0..1" Receipt : genere
NfcPaymentToken "0..*" --> "1" Wallet : reserveMontant
NfcPaymentToken "0..1" --> "0..1" PaymentTransaction : regularise

class Notification {
  +UUID id
  +NotificationType type
  +String title
  +String body
  +NotificationStatus status
  +String targetDeviceToken
  +DateTime scheduledAt
  +DateTime sentAt
  +DateTime readAt
  +send()
  +retry()
  +markRead()
}

class NotificationPreference {
  +UUID id
  +Boolean pushEnabled
  +Boolean bookingUpdates
  +Boolean paymentAlerts
  +Boolean marketingEnabled
  +update()
  +allows(type) Boolean
}

User "1" *-- "0..*" Notification : recoit
User "1" *-- "1" NotificationPreference : configure
Notification "0..*" --> "0..1" Booking : reference
Notification "0..*" --> "0..1" PaymentTransaction : reference
Notification "0..*" --> "0..1" Trip : reference

class OfflineMapArea {
  +UUID id
  +String areaName
  +String boundingBox
  +Long sizeBytes
  +DateTime downloadedAt
  +DateTime expiresAt
  +download()
  +refresh()
  +delete()
}

class OfflineOperation {
  +UUID id
  +OfflineOperationType type
  +String payload
  +String idempotencyKey
  +OfflineOperationStatus status
  +Integer retryCount
  +DateTime queuedAt
  +DateTime syncedAt
  +enqueue()
  +markSynced()
  +markConflict(reason)
}

class SyncBatch {
  +UUID id
  +DateTime startedAt
  +DateTime completedAt
  +SyncStatus status
  +sync()
  +resolveConflicts()
}

class SyncConflict {
  +UUID id
  +String code
  +String message
  +String resolution
  +DateTime detectedAt
  +explainToUser()
  +resolve()
}

User "1" *-- "0..*" OfflineMapArea : telecharge
User "1" *-- "0..*" OfflineOperation : empile
SyncBatch "1" *-- "1..*" OfflineOperation : synchronise
OfflineOperation "0..1" *-- "0..1" SyncConflict : conflit
OfflineOperation "0..*" --> "0..1" Booking : actionReservation
OfflineOperation "0..*" --> "0..1" NfcPaymentToken : actionNfc

class Rating {
  +UUID id
  +Integer stars
  +String comment
  +RatingStatus status
  +DateTime createdAt
  +submit()
  +flag()
  +hide()
}

class SupportTicket {
  +UUID id
  +SupportCategory category
  +Priority priority
  +TicketStatus status
  +String subject
  +String message
  +DateTime createdAt
  +DateTime closedAt
  +create()
  +assign()
  +reply(message)
  +close()
}

class ModerationAction {
  +UUID id
  +ModerationTargetType targetType
  +String targetId
  +ModerationActionType action
  +String reason
  +DateTime actedAt
  +apply()
  +audit()
}

Trip "1" --> "0..*" Rating : autoriseApresFin
User "1" --> "0..*" Rating : evalue
Profile "1" <-- "0..*" Rating : estEvalue
User "1" *-- "0..*" SupportTicket : cree
AdminProfile "1" --> "0..*" SupportTicket : traite
AdminProfile "1" --> "0..*" ModerationAction : effectue
ModerationAction "0..*" --> "0..1" Rating : cible
ModerationAction "0..*" --> "0..1" SupportTicket : cible

class AiRecommendationRequest {
  +UUID id
  +String departureCity
  +String destinationCity
  +String quarter
  +TimeRange preferredTime
  +Integer requestedSeats
  +String anonymizedUserContext
  +buildFeatures()
}

class AiRecommendation {
  +UUID id
  +Double score
  +String reason
  +DateTime generatedAt
  +Boolean clicked
  +rank()
  +markClicked()
}

class AiUsageMetric {
  +UUID id
  +Date period
  +Integer callCount
  +Integer acceptedSuggestionCount
  +Double averageLatencyMs
  +Integer errorCount
  +aggregate()
}

class KpiSnapshot {
  +UUID id
  +Date periodStart
  +Date periodEnd
  +Integer monthlyActiveUsers
  +Integer activeVerifiedDrivers
  +Integer reservationCount
  +Decimal walletVolumeFcfa
  +Double slaAvailability
  +Double avgSigSyncSeconds
  +Double aiUsageRate
  +calculate()
  +exportCsv()
}

AiRecommendationRequest "1" --> "0..*" IntercityAnnouncement : analyse
AiRecommendationRequest "1" *-- "0..*" AiRecommendation : retourne
AiRecommendation "0..*" --> "1" IntercityAnnouncement : suggere
AiUsageMetric "0..*" --> "0..*" AiRecommendation : agrege
KpiSnapshot ..> User : compteMAU
KpiSnapshot ..> DriverProfile : compteConducteurs
KpiSnapshot ..> Booking : compteReservations
KpiSnapshot ..> PaymentTransaction : volumeWallet
KpiSnapshot ..> AiUsageMetric : tauxIA

class AuthService {
  +register(phone, password, cgu) User
  +sendOtp(user, purpose)
  +verifyOtp(phone, code) Boolean
  +login(phone, password) Session
  +refresh(refreshToken) Session
  +logout(sessionId)
  +resetPassword(phone, otp, newPassword)
}

class ProfileService {
  +createProfile(userId, type, data) Profile
  +switchActiveProfile(userId, profileId)
  +updateProfile(profileId, data)
  +checkPermission(profileId, action) Boolean
}

class KycService {
  +requiredDocuments(profileType) List
  +uploadDocument(profileId, file, type) OfficialDocument
  +encryptSensitiveData(documentId)
  +reviewProfile(profileId, decision, reason)
  +auditAccess(documentId, adminId)
}

class GeoService {
  +ingestDriverPosition(driverId, point)
  +getLastPosition(driverId) DriverPosition
  +findNearbyDrivers(origin, radiusKm) List
  +publishTrafficLayer(layer)
}

class PricingService {
  +estimateFare(origin, destination, context) FareEstimate
  +createPricingRule(rule) PricingRule
  +traceAppliedRules(estimateId)
}

class IntercityService {
  +createAnnouncement(profileId, data) IntercityAnnouncement
  +searchAnnouncements(criteria) List
  +updateAnnouncement(id, data)
  +closeAnnouncement(id)
}

class BookingService {
  +requestBooking(passengerId, announcementId, seats) Booking
  +lockSeat(announcementId, seats) SeatLock
  +confirmBooking(bookingId)
  +cancelBooking(bookingId, reason)
  +expireUnpaidBookings()
}

class WalletService {
  +createWallet(userId) Wallet
  +getBalance(walletId) Decimal
  +postLedgerEntry(walletId, entry)
  +getLedger(walletId) List
}

class PaymentService {
  +initiateDeposit(walletId, provider, amount) MobileMoneyDeposit
  +handleCallback(provider, payload)
  +payTrip(bookingId, passengerWalletId) RidePayment
  +generateReceipt(paymentId) Receipt
}

class NfcPaymentService {
  +issueToken(walletId, amount) NfcPaymentToken
  +exchangeToken(token)
  +validateDeferredToken(token)
  +regularizeLedger(tokenId)
}

class NotificationService {
  +sendBookingNotification(bookingId, type)
  +sendPaymentAlert(paymentId)
  +applyPreferences(userId, type) Boolean
  +retryFailed()
}

class TrackingService {
  +changeTripStatus(tripId, status) TripEvent
  +getActiveTripForPassenger(passengerId) Trip
  +attachDriverPosition(tripId, positionId)
  +closeTrip(tripId)
}

class OfflineSyncService {
  +syncBatch(userId, operations) SyncBatch
  +ensureIdempotency(operation)
  +resolveConflict(operationId, resolution)
  +auditDeferredOperation(operationId)
}

class SupportService {
  +createTicket(userId, category, message) SupportTicket
  +reply(ticketId, message)
  +changeStatus(ticketId, status)
}

class RatingService {
  +submitRating(tripId, raterId, ratedProfileId, stars, comment) Rating
  +recalculateAverage(profileId)
  +flagRating(ratingId)
}

class AiRecommendationService {
  +recommend(request) List
  +fallbackSearch(criteria) List
  +logMetric(metric)
}

class AnalyticsService {
  +computeKpi(period) KpiSnapshot
  +computeAiMetrics(period) AiUsageMetric
  +exportCsv(snapshotId)
  +anonymize()
}

class ApiGateway {
  +route(path, request)
  +validateJwt(token)
  +enforceRbac(route, role)
  +rateLimit(key)
  +verifyCallbackSignature(payload)
  +openApiAggregate()
}

AuthService ..> User : <<include>> creerCompte
AuthService ..> OtpCode : <<include>> verifierTelephone
AuthService ..> Session : <<include>> ouvrirSession
ProfileService ..> Profile : <<include>> gererProfil
KycService ..> OfficialDocument : <<include>> televerserDocument
KycService ..> ValidationDecision : <<include>> validerOuRejeter
KycService ..> NotificationService : <<include>> notifierDecision
GeoService ..> DriverPosition : <<include>> publierGPS
PricingService ..> FareEstimate : <<include>> calculerPrix
IntercityService ..> IntercityAnnouncement : <<include>> publierAnnonce
BookingService ..> Booking : <<include>> souscrire
BookingService ..> SeatLock : <<include>> verrouillerPlace
BookingService ..> PaymentService : <<extend>> paiementRequis
PaymentService ..> WalletService : <<include>> debitCreditAtomique
PaymentService ..> Receipt : <<include>> genererRecu
NfcPaymentService ..> PaymentService : <<extend>> paiementHorsLigne
OfflineSyncService ..> NfcPaymentService : <<include>> validerJetonDiffere
OfflineSyncService ..> BookingService : <<include>> synchroniserReservation
NotificationService ..> Notification : <<include>> push
TrackingService ..> TripEvent : <<include>> journaliserStatut
TrackingService ..> PaymentService : <<extend>> paiementFinal
RatingService ..> Rating : <<extend>> postTrajet
SupportService ..> SupportTicket : <<extend>> assistance
AiRecommendationService ..> IntercityService : <<include>> annoncesExistantes
AiRecommendationService ..> AnalyticsService : <<include>> metriquesIA
AiRecommendationService ..> IntercityService : <<extend>> fallbackDeterministe
AnalyticsService ..> KpiSnapshot : <<include>> consoliderKpi
ApiGateway ..> AuthService : <<include>> jwt
ApiGateway ..> ProfileService : <<include>> rbac
ApiGateway ..> PaymentService : <<include>> callbacksMobileMoney
ApiGateway ..> AiRecommendationService : <<include>> routerIA

class UserStatus {
  <<enumeration>>
  PENDING_PHONE_VERIFICATION
  ACTIVE
  SUSPENDED
  DELETED
}
class SessionStatus {
  <<enumeration>>
  ACTIVE
  EXPIRED
  REVOKED
}
class OtpPurpose {
  <<enumeration>>
  REGISTRATION
  LOGIN
  PASSWORD_RESET
  PAYMENT_CONFIRMATION
}
class ProfileType {
  <<enumeration>>
  PASSENGER
  MOTOTAXI
  TRANSPORTER
  AGENCY
  ADMIN
}
class AdminRole {
  <<enumeration>>
  PLATFORM_ADMIN
  SUPPORT_AGENT
  REGULATOR
  AGENCY_MANAGER
}
class ValidationStatus {
  <<enumeration>>
  DRAFT
  PENDING
  VALIDATED
  REJECTED
  SUSPENDED
}
class DocumentType {
  <<enumeration>>
  CNI
  DRIVER_LICENSE
  VEHICLE_REGISTRATION
  INSURANCE
  PROFILE_PHOTO
  AGENCY_DOCUMENT
}
class DocumentStatus {
  <<enumeration>>
  UPLOADED
  ENCRYPTED
  PENDING_REVIEW
  ACCEPTED
  REJECTED
  EXPIRED
}
class VehicleType {
  <<enumeration>>
  MOTORCYCLE
  CAR
  VAN
  BUS
}
class DecisionType {
  <<enumeration>>
  VALIDATE
  REJECT
  SUSPEND
}
class AccessAction {
  <<enumeration>>
  VIEW
  DOWNLOAD
  APPROVE
  REJECT
}
class DriverAvailability {
  <<enumeration>>
  AVAILABLE
  OCCUPIED
  OFFLINE
}
class RouteStatus {
  <<enumeration>>
  PROPOSED
  ACTIVE
  BLOCKED
  ARCHIVED
}
class TrafficLayerType {
  <<enumeration>>
  CONGESTION
  ROADWORK
  DAMAGED_ROAD
  ALTERNATIVE_ROUTE
}
class Severity {
  <<enumeration>>
  LOW
  MEDIUM
  HIGH
  CRITICAL
}
class TimeWindow {
  +Time startAt
  +Time endAt
  +includes(time) Boolean
}
class TimeRange {
  +Time startAt
  +Time endAt
  +overlaps(other) Boolean
}
class AnnouncementStatus {
  <<enumeration>>
  DRAFT
  PUBLISHED
  FULL
  CLOSED
  CANCELLED
}
class BookingStatus {
  <<enumeration>>
  PENDING
  CONFIRMED
  CANCELLED
  EXPIRED
  PAID
}
class TripType {
  <<enumeration>>
  URBAN
  INTERCITY
}
class TripStatus {
  <<enumeration>>
  CONFIRMED
  APPROACHING
  ON_BOARD
  COMPLETED
  CANCELLED
}
class WalletStatus {
  <<enumeration>>
  ACTIVE
  FROZEN
  CLOSED
}
class LedgerEntryType {
  <<enumeration>>
  CREDIT
  DEBIT
  REVERSAL
  HOLD
  RELEASE
}
class PaymentType {
  <<enumeration>>
  MOBILE_MONEY_DEPOSIT
  RIDE_PAYMENT
  NFC_PAYMENT
  REFUND
}
class PaymentStatus {
  <<enumeration>>
  INITIATED
  PENDING_CALLBACK
  CONFIRMED
  FAILED
  EXPIRED
  REFUNDED
}
class PaymentProvider {
  <<enumeration>>
  MTN_MOMO
  ORANGE_MONEY
  AGGREGATOR
  WALLET
  NFC
}
class NfcTokenStatus {
  <<enumeration>>
  ISSUED
  EXCHANGED
  PENDING_SYNC
  CONSUMED
  EXPIRED
  REJECTED
}
class NotificationType {
  <<enumeration>>
  SUBSCRIPTION
  CONFIRMATION
  CANCELLATION
  DEPARTURE_REMINDER
  STATUS_CHANGE
  PAYMENT
  KYC_DECISION
}
class NotificationStatus {
  <<enumeration>>
  QUEUED
  SENT
  READ
  FAILED
}
class OfflineOperationType {
  <<enumeration>>
  BOOKING_CREATE
  BOOKING_CANCEL
  NFC_PAYMENT
  PROFILE_UPDATE
  SUPPORT_TICKET
}
class OfflineOperationStatus {
  <<enumeration>>
  QUEUED
  SYNCING
  SYNCED
  CONFLICT
  FAILED
}
class SyncStatus {
  <<enumeration>>
  STARTED
  PARTIAL_SUCCESS
  COMPLETED
  FAILED
}
class RatingStatus {
  <<enumeration>>
  PUBLISHED
  FLAGGED
  HIDDEN
}
class SupportCategory {
  <<enumeration>>
  TRANSPORT
  PAYMENT
  ACCOUNT
  SECURITY
  EMERGENCY
}
class Priority {
  <<enumeration>>
  LOW
  MEDIUM
  HIGH
  URGENT
}
class TicketStatus {
  <<enumeration>>
  OPEN
  IN_PROGRESS
  WAITING_USER
  RESOLVED
  CLOSED
}
class ModerationTargetType {
  <<enumeration>>
  RATING
  SUPPORT_TICKET
  USER
  PROFILE
  ANNOUNCEMENT
}
class ModerationActionType {
  <<enumeration>>
  HIDE
  RESTORE
  ESCALATE
  CLOSE
  SUSPEND
}

User --> UserStatus
Session --> SessionStatus
OtpCode --> OtpPurpose
Profile --> ProfileType
AdminProfile --> AdminRole
Profile --> ValidationStatus
OfficialDocument --> DocumentType
OfficialDocument --> DocumentStatus
Vehicle --> VehicleType
ValidationDecision --> DecisionType
DocumentAccessAudit --> AccessAction
DriverProfile --> DriverAvailability
Route --> RouteStatus
TrafficLayer --> TrafficLayerType
TrafficLayer --> Severity
PricingRule --> TimeWindow
AnnouncementSearchCriteria --> TimeRange
IntercityAnnouncement --> AnnouncementStatus
Booking --> BookingStatus
Trip --> TripType
Trip --> TripStatus
Wallet --> WalletStatus
LedgerEntry --> LedgerEntryType
PaymentTransaction --> PaymentType
PaymentTransaction --> PaymentStatus
MobileMoneyDeposit --> PaymentProvider
NfcPaymentToken --> NfcTokenStatus
Notification --> NotificationType
Notification --> NotificationStatus
OfflineOperation --> OfflineOperationType
OfflineOperation --> OfflineOperationStatus
SyncBatch --> SyncStatus
Rating --> RatingStatus
SupportTicket --> SupportCategory
SupportTicket --> Priority
SupportTicket --> TicketStatus
ModerationAction --> ModerationTargetType
ModerationAction --> ModerationActionType
```
