# Tickets - motodi-go-backend-core

## Labels communs

`repo:motodi-go-backend-core`, `project:moto-di-go`

# CORE-DATA-001 - Modele metier PostgreSQL/PostGIS

## Priorite

HIGH

## Delai estime

3d

## Dependances

- DEVOPS-ENV-001

## Objectif

En tant que developpeur backend, je veux modeliser les donnees metier et geospatiales afin de supporter profils, trajets, documents, annonces, reservations, wallet et positions GPS.

## Taches

- Creer migrations utilisateurs, roles, documents, vehicules, annonces, reservations, wallet, transactions et positions.
- Activer PostGIS et index spatiaux.
- Definir contraintes d'integrite, statuts et relations principales.
- Documenter le modele pour mobile, gateway et dashboard.

## Criteres d'acceptation

- Les migrations sont rejouables sur base vide.
- Les colonnes geographiques permettent distance, proximite et itineraire.
- Les entites critiques ont index et contraintes.
- Un diagramme ou document de modele est disponible.

## References

- Backlog: MGO-002
- Cahier des charges: section 5.1

# CORE-AUTH-001 - Comptes, OTP, connexion et recuperation d'acces

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-DATA-001
- GATE-PLAT-001

## Objectif

En tant qu'utilisateur, je veux creer un compte, verifier mon telephone et me reconnecter afin d'acceder aux services de transport.

## Taches

- Implementer inscription par telephone camerounais, mot de passe et acceptation CGU.
- Ajouter generation, validation et expiration OTP.
- Ajouter login, refresh token, logout et reset mot de passe via OTP.
- Journaliser tentatives suspectes et erreurs de validation.

## Criteres d'acceptation

- Un numero camerounais valide peut creer un compte apres OTP.
- Un numero deja inscrit retourne un message clair.
- La session expire selon la politique de securite.
- Le reset mot de passe fonctionne sans exposer l'existence du compte de facon dangereuse.

## References

- Backlog: MGO-004, MGO-005
- Cahier des charges: F-01, section 6.2

# CORE-PROF-001 - Profils multi-roles et permissions

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-AUTH-001

## Objectif

En tant qu'utilisateur, je veux choisir et gerer mes profils passager, mototaxi, transporteur ou agence afin d'utiliser les fonctionnalites adaptees a mon role.

## Taches

- Modeliser profils et statuts de validation.
- Ajouter endpoints creation, edition et lecture profil courant.
- Appliquer permissions backend par role.
- Exposer le statut de validation et les actions autorisees.

## Criteres d'acceptation

- Un utilisateur peut avoir plusieurs profils compatibles.
- Les endpoints refusent les actions hors role.
- Les informations specifiques a chaque profil sont sauvegardees.
- Le statut validation est disponible pour mobile et dashboard.

## References

- Backlog: MGO-006
- Cahier des charges: F-01

# CORE-KYC-001 - Documents officiels, chiffrement et audit d'acces

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-PROF-001

## Objectif

En tant que conducteur ou transporteur, je veux televerser mes documents officiels et les proteger afin de faire valider ma fiabilite.

## Taches

- Implementer types de documents par profil : CNI, permis, carte grise, assurance, photo.
- Controler formats, taille et statut de chaque piece.
- Chiffrer donnees sensibles au repos avec gestion de cles hors code source.
- Journaliser les consultations administratives.

## Criteres d'acceptation

- Les documents requis dependent du profil.
- Les fichiers invalides sont refuses avec erreur localisable.
- Les donnees sensibles sont chiffrees avant stockage.
- L'acces aux documents est limite aux roles autorises et auditable.

## References

- Backlog: MGO-007, MGO-008
- Cahier des charges: F-02, F-03, section 6.2

# CORE-KYC-002 - Workflow de validation administrative des profils

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-KYC-001

## Objectif

En tant qu'administrateur, je veux valider ou rejeter les profils transporteurs afin d'empecher les acteurs non conformes d'operer.

## Taches

- Exposer file des dossiers en attente.
- Ajouter decision validation/rejet avec motif obligatoire.
- Conserver historique des decisions.
- Declencher notification utilisateur apres decision.

## Criteres d'acceptation

- Seuls les administrateurs autorises peuvent statuer.
- Un rejet exige un motif.
- Le statut profil change immediatement apres decision.
- L'historique reste consultable pour audit.

## References

- Backlog: MGO-009
- Cahier des charges: F-02

# CORE-GEO-001 - Ingestion GPS conducteurs et stockage geospatial

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-KYC-002
- CORE-DATA-001

## Objectif

En tant que conducteur valide, je veux transmettre ma position en temps reel afin d'etre visible pour les passagers et le suivi.

## Taches

- Creer endpoint ingestion position conducteur.
- Valider consentement, profil actif et frequence cible configurable.
- Stocker derniere position et historique utile avec index PostGIS.
- Prevoir nettoyage ou retention des positions.

## Criteres d'acceptation

- Seuls les conducteurs valides peuvent publier une position.
- Une frequence anormale est limitee.
- Les services autorises peuvent lire la derniere position.
- Les tests couvrent charge, index et donnees invalides.

## References

- Backlog: MGO-011
- Cahier des charges: section 6.1

# CORE-PRICE-001 - Calcul itineraire, distance et tarif standardise

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-GEO-001

## Objectif

En tant que passager, je veux connaitre le cout standardise avant de m'engager afin d'eviter la tarification opaque.

## Taches

- Calculer distance, duree estimee et prix selon regles parametrees.
- Gerer zone, jour/nuit, meteo ou coefficient admin.
- Exposer simulation tarifaire et trace des regles appliquees.
- Ajouter tests d'arrondis et scenarios limites.

## Criteres d'acceptation

- Origine et destination retournent distance, duree et prix.
- Le prix est reproductible et auditable.
- Les regles tarifaires peuvent etre parametrees par admin.
- Les cas jour/nuit et zones differentes sont testes.

## References

- Backlog: MGO-012
- Cahier des charges: F-07
- BMC: standardisation tarifaire comme proposition de valeur

# CORE-URBAN-001 - Recherche de mototaxis disponibles

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-GEO-001
- CORE-PRICE-001

## Objectif

En tant que passager urbain, je veux rechercher un mototaxi disponible et comparer le prix estime afin de choisir rapidement un trajet.

## Taches

- Rechercher conducteurs valides proches via PostGIS.
- Filtrer par disponibilite et distance d'approche.
- Retourner prix estime coherent avec le calculateur.
- Prevoir pagination et tri par proximite.

## Criteres d'acceptation

- Aucun conducteur non valide n'est propose.
- Les resultats incluent distance d'approche, disponibilite et prix.
- Les requetes spatiales restent performantes avec volume de positions.
- Les etats aucun resultat sont explicables au client.

## References

- Backlog: MGO-014
- Cahier des charges: F-06, F-07

# CORE-INTER-001 - Annonces interurbaines et recherche multicritere

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-KYC-002

## Objectif

En tant que transporteur ou voyageur, je veux publier et rechercher des annonces interurbaines afin de rendre visibles horaires, prix et places disponibles.

## Taches

- Implementer CRUD annonces avec depart, destination, date, heure, prix, places, vehicule et consignes.
- Reserver la creation aux profils valides.
- Ajouter recherche par ville, date, prix, horaire, quartier et disponibilite.
- Ajouter pagination, tri et controles de coherence horaire.

## Criteres d'acceptation

- Une annonce incomplete ou non autorisee est refusee.
- Les filtres sont cumulables.
- Les places restantes sont visibles.
- Aucun resultat retourne des suggestions d'elargissement.

## References

- Backlog: MGO-015, MGO-016
- Cahier des charges: F-04, F-09

# CORE-BOOK-001 - Souscription, reservation et verrouillage de place

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-INTER-001
- CORE-PAY-001

## Objectif

En tant que voyageur, je veux souscrire a une annonce et bloquer ma place afin d'eviter de perdre mon trajet apres confirmation.

## Taches

- Creer demandes de participation avec statuts en attente, confirme, annule.
- Verrouiller les places de facon transactionnelle.
- Gerer expiration automatique si paiement ou confirmation echoue.
- Exposer statut pour voyageur et transporteur.

## Criteres d'acceptation

- Une souscription est impossible si le trajet est complet.
- Deux reservations concurrentes ne peuvent pas surconsommer les places.
- Le statut est visible par les deux parties.
- Les annulations respectent les regles metier.

## References

- Backlog: MGO-017, MGO-023
- Cahier des charges: F-10

# CORE-WALLET-001 - Wallet, solde et ledger comptable

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-AUTH-001

## Objectif

En tant qu'utilisateur, je veux disposer d'un portefeuille electronique afin de gerer mes fonds de transport dans l'application.

## Taches

- Creer wallet automatiquement a l'activation du compte.
- Implementer solde, devise FCFA et mouvements horodates.
- Garantir integrite comptable et idempotence.
- Exposer historique de mouvements.

## Criteres d'acceptation

- Chaque utilisateur actif a un wallet unique.
- Les mouvements sont immuables ou audites.
- Le solde derive des mouvements reste coherent.
- Les tests couvrent concurrence et rollback transactionnel.

## References

- Backlog: MGO-019
- Cahier des charges: F-08

# CORE-PAY-001 - Depot Mobile Money et callbacks signes

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-WALLET-001
- GATE-INTEGRATIONS-001

## Objectif

En tant qu'utilisateur, je veux alimenter mon wallet via MTN MoMo, Orange Money ou un agregateur afin de payer mes trajets sans especes.

## Taches

- Creer initiation de depot Mobile Money.
- Traiter callbacks de confirmation, echec et expiration.
- Crediter le wallet une seule fois par transaction valide.
- Exposer donnees de reconciliation pour administration.

## Criteres d'acceptation

- Un depot valide credite le wallet exactement une fois.
- Un echec ou une expiration est visible cote client.
- Les callbacks sont signes et idempotents.
- Les transactions sont reconciliables par reference externe.

## References

- Backlog: MGO-020
- Cahier des charges: F-08, section 6.2
- BMC: MTN MoMo et Orange Money comme partenaires cles

# CORE-PAY-002 - Paiement trajet, repartition et recu

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-PAY-001
- CORE-BOOK-001

## Objectif

En tant que passager, je veux payer un trajet via mon wallet afin de securiser la transaction avec conducteur ou transporteur.

## Taches

- Debiter passager et crediter transporteur selon regles.
- Refuser paiement si solde insuffisant.
- Mettre a jour statut reservation ou trajet apres paiement.
- Generer recu consultable.

## Criteres d'acceptation

- Debit et credit sont atomiques.
- Un solde insuffisant ne modifie aucune reservation.
- Le recu contient montant, date, trajet et statut.
- Les erreurs paiement sont localisables.

## References

- Backlog: MGO-021
- Cahier des charges: F-08

# CORE-NFC-001 - Jetons de paiement NFC hors-ligne

## Priorite

MEDIUM

## Delai estime

5d

## Dependances

- CORE-PAY-002

## Objectif

En tant qu'utilisateur, je veux payer sans connexion via NFC afin de finaliser un trajet malgre l'instabilite reseau.

## Taches

- Emettre jetons signes, limites dans le temps et lies a un montant.
- Valider les jetons au retour de connectivite.
- Prevenir double depense par identifiant unique.
- Exposer statut `en attente de synchronisation`.

## Criteres d'acceptation

- Un jeton expire ou deja consomme est refuse.
- Les paiements hors-ligne restent auditables.
- Une synchronisation valide regularise le ledger.
- Les tests couvrent double depense et replay.

## References

- Backlog: MGO-022
- Cahier des charges: F-13
- BMC: mode hors-ligne NFC comme avantage concurrentiel

# CORE-NOTIF-001 - Notifications push et historique minimal

## Priorite

HIGH

## Delai estime

2d

## Dependances

- CORE-BOOK-001

## Objectif

En tant que transporteur ou passager, je veux recevoir les notifications de souscription, confirmation, annulation et depart afin d'organiser mon trajet.

## Taches

- Integrer fournisseur push FCM/APNs cote backend.
- Gerer preferences de notifications.
- Declencher notifications sur reservation, paiement et statut trajet.
- Conserver historique minimal.

## Criteres d'acceptation

- Le transporteur est notifie apres nouvelle confirmation.
- Le passager est notifie lors d'un changement de statut.
- Les preferences utilisateur sont respectees.
- Les echecs push sont retries ou journalises.

## References

- Backlog: MGO-024
- Cahier des charges: F-10

# CORE-TRACK-001 - Statuts de course et suivi d'embarquement

## Priorite

HIGH

## Delai estime

3d

## Dependances

- CORE-GEO-001
- CORE-NOTIF-001

## Objectif

En tant que passager, je veux suivre le statut de mon trajet afin de savoir si le conducteur est en approche, en cours ou arrive.

## Taches

- Modeliser statuts confirme, en approche, a bord, termine, annule.
- Valider transitions autorisees.
- Associer position conducteur quand trajet actif.
- Declencher recu ou paiement final a la fin du trajet.

## Criteres d'acceptation

- Les transitions invalides sont refusees.
- Les evenements sont horodates.
- Le passager ne voit que ses trajets actifs.
- La fin de trajet alimente historique et post-trajet.

## References

- Backlog: MGO-025
- Cahier des charges: F-10, F-12

# CORE-OFFLINE-001 - Synchronisation des actions hors connexion

## Priorite

MEDIUM

## Delai estime

5d

## Dependances

- CORE-NFC-001
- CORE-TRACK-001

## Objectif

En tant qu'utilisateur, je veux synchroniser mes actions hors connexion afin que reservations et paiements soient regularises quand le reseau revient.

## Taches

- Creer endpoint de synchronisation par lot.
- Gerer idempotence, ordre, conflits et erreurs partielles.
- Journaliser operations differees pour audit back-office.
- Retourner resultats exploitables par l'application mobile.

## Criteres d'acceptation

- Une operation rejouee ne cree pas de doublon.
- Les conflits sont expliques avec code stable.
- Les operations validees mettent a jour reservations ou paiements.
- Les erreurs de synchronisation sont auditables.

## References

- Backlog: MGO-027
- Cahier des charges: F-13

# CORE-POST-001 - Notation, commentaires, historique et support

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- CORE-TRACK-001
- CORE-PAY-002

## Objectif

En tant qu'utilisateur, je veux noter, consulter mon historique et contacter le support afin de suivre et ameliorer mon experience de transport.

## Taches

- Ajouter notation reciproque apres trajet termine.
- Calculer score moyen visible sur profils.
- Exposer historique trajets passes/a venir avec recus.
- Creer tickets support categorises.

## Criteres d'acceptation

- Une notation n'est possible qu'apres trajet termine.
- Le score moyen est recalcule automatiquement.
- L'historique se filtre par date et statut.
- Un ticket support cree un accuse de reception.

## References

- Backlog: MGO-029, MGO-030, MGO-031
- Cahier des charges: F-11, F-12, F-14

# CORE-ANALYTICS-001 - API KPI produit, technique et revenus

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- CORE-INTER-001
- CORE-PAY-001
- CORE-TRACK-001
- AI-METRICS-001

## Objectif

En tant que decideur, je veux consulter des KPI agregees afin d'evaluer adoption, congestion, fiabilite et performance economique.

## Taches

- Calculer MAU, conducteurs verifies actifs, reservations, volume wallet et disponibilite.
- Ajouter temps moyen de synchronisation SIG et taux utilisation IA.
- Agreger sans exposer les donnees sensibles.
- Exposer endpoints filtrables par periode et export CSV.

## Criteres d'acceptation

- Les KPI sont consultables par periode.
- Les donnees sensibles sont anonymisees ou agregees.
- Les exports CSV sont coherents avec les donnees affichees.
- Les cibles BMC principales peuvent etre suivies.

## References

- Backlog: MGO-033
- Cahier des charges: section 8.2
- BMC: MAU, 2 000 drivers M6, SLA 99.9%, volume MoMo/Orange

