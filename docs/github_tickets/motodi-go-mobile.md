# Tickets - motodi-go-mobile

## Labels communs

`repo:motodi-go-mobile`, `project:moto-di-go`

# MOB-AUTH-001 - Authentification mobile et profils multi-roles

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-AUTH-001
- CORE-PROF-001
- GATE-AUTH-001

## Objectif

En tant qu'utilisateur mobile, je veux m'inscrire, verifier mon telephone, me connecter et choisir mon profil afin d'acceder aux parcours passager, mototaxi, transporteur ou agence.

## Taches

- Creer ecrans inscription, OTP, connexion, mot de passe oublie et deconnexion.
- Ajouter persistance session, refresh token et etats expiration.
- Ajouter selection et edition des profils.
- Gerer validations client telephone Cameroun, mot de passe et erreurs localisables.

## Criteres d'acceptation

- Inscription et verification OTP fonctionnent.
- Connexion persistante et deconnexion fonctionnent.
- Le profil actif conditionne la navigation.
- Les etats loading, empty, error et success sont geres.

## References

- Backlog: MGO-004, MGO-005, MGO-006
- Cahier des charges: F-01

# MOB-KYC-001 - Televersement documents et suivi validation

## Priorite

HIGH

## Delai estime

3d

## Dependances

- MOB-AUTH-001
- CORE-KYC-001
- CORE-KYC-002

## Objectif

En tant que conducteur ou transporteur, je veux televerser mes documents et suivre leur statut afin d'obtenir un profil verifie.

## Taches

- Creer parcours documents par type de profil.
- Ajouter capture/selection fichier, previsualisation et controle taille/format.
- Afficher statuts en attente, valide, rejete avec motif.
- Notifier l'utilisateur apres decision admin.

## Criteres d'acceptation

- Les pieces requises changent selon le profil.
- Les fichiers invalides sont bloques cote client.
- Le motif de rejet est visible et actionnable.
- Un profil non valide ne peut pas acceder aux fonctions conducteur/transporteur.

## References

- Backlog: MGO-007, MGO-009
- Cahier des charges: F-02, F-03

# MOB-MAP-001 - Carte urbaine, geolocalisation et permissions

## Priorite

HIGH

## Delai estime

5d

## Dependances

- GATE-PLAT-001
- CORE-DATA-001

## Objectif

En tant que passager, je veux voir une carte urbaine et ma position afin de comprendre les options de transport autour de moi.

## Taches

- Integrer Mapbox ou OpenStreetMap dans Flutter.
- Demander et gerer consentement GPS.
- Afficher position utilisateur, zoom et deplacement fluide.
- Gerer refus permission, GPS indisponible et mode degrade.

## Criteres d'acceptation

- La carte charge sur Android et iOS.
- La position est affichee apres consentement.
- Le refus GPS affiche une alternative claire.
- Les performances restent acceptables sur mobile milieu de gamme.

## References

- Backlog: MGO-010
- Cahier des charges: F-06

# MOB-DRIVER-001 - Disponibilite conducteur et partage GPS

## Priorite

HIGH

## Delai estime

3d

## Dependances

- MOB-KYC-001
- CORE-GEO-001
- GATE-REALTIME-001

## Objectif

En tant que conducteur valide, je veux activer ma disponibilite et transmettre ma position afin d'etre visible des passagers.

## Taches

- Ajouter mode conducteur disponible/occupe/hors ligne.
- Transmettre position selon frequence configurable.
- Afficher indicateur batterie, GPS et reseau.
- Stopper l'envoi en logout, refus GPS ou profil non valide.

## Criteres d'acceptation

- Seuls les profils valides peuvent passer disponibles.
- La position est envoyee uniquement avec consentement.
- L'utilisateur voit clairement l'etat de diffusion GPS.
- La consommation batterie est limitee par strategie d'envoi.

## References

- Backlog: MGO-011, MGO-014
- Cahier des charges: section 6.1

# MOB-URBAN-001 - Recherche mototaxi, itineraire et prix standardise

## Priorite

HIGH

## Delai estime

5d

## Dependances

- MOB-MAP-001
- CORE-PRICE-001
- CORE-URBAN-001

## Objectif

En tant que passager urbain, je veux choisir origine/destination, voir le prix et comparer les mototaxis proches afin de reserver sans negociation opaque.

## Taches

- Ajouter selection origine/destination sur carte.
- Afficher distance, duree, prix estime et regle appliquee.
- Lister conducteurs proches avec disponibilite et distance d'approche.
- Gerer aucun resultat, reseau lent et recalcul.

## Criteres d'acceptation

- Le prix est visible avant engagement.
- Les conducteurs proposes sont valides et disponibles.
- L'utilisateur comprend pourquoi aucun conducteur n'est propose.
- Les erreurs API sont reprises sans bloquer la carte.

## References

- Backlog: MGO-012, MGO-014
- Cahier des charges: F-07

# MOB-TRAFFIC-001 - Couches trafic et itineraire alternatif

## Priorite

MEDIUM

## Delai estime

5d

## Dependances

- MOB-MAP-001
- CORE-PRICE-001
- ADMIN-PRICING-001

## Objectif

En tant qu'usager, je veux voir le trafic et les voies alternatives afin d'eviter les routes impraticables ou saturees.

## Taches

- Afficher couches routes congestionnees, travaux et routes degradees.
- Ajouter legende simple et etats de synchronisation.
- Proposer itineraire alternatif si route principale bloquee.
- Mettre en cache les donnees utiles pour reprise reseau.

## Criteres d'acceptation

- Les couches trafic sont activables sur la carte.
- Un itineraire alternatif est propose quand disponible.
- Les donnees obsoletes sont signalees.
- Le rendu carte reste fluide.

## References

- Backlog: MGO-013
- Cahier des charges: F-06

# MOB-INTER-001 - Parcours interurbain publication, recherche et souscription

## Priorite

HIGH

## Delai estime

5d

## Dependances

- MOB-KYC-001
- CORE-INTER-001
- CORE-BOOK-001

## Objectif

En tant que transporteur ou voyageur, je veux publier, rechercher et souscrire a des trajets interurbains afin de remplir les places et planifier mon depart.

## Taches

- Creer formulaire publication annonce pour transporteurs valides.
- Ajouter recherche avec filtres ville, date, prix, horaire, quartier et disponibilite.
- Creer detail annonce et demande de souscription.
- Afficher statuts en attente, confirme, annule.

## Criteres d'acceptation

- Une annonce incomplete affiche des validations claires.
- Les filtres sont cumulables et conserves pendant la session.
- Une souscription impossible explique la raison.
- Le decompte places est rafraichi apres action.

## References

- Backlog: MGO-015, MGO-016, MGO-017
- Cahier des charges: F-04, F-09

# MOB-AI-001 - Interface assistant IA covoiturage

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- MOB-INTER-001
- AI-MATCH-001

## Objectif

En tant que voyageur, je veux obtenir des recommandations expliquees afin de choisir le trajet interurbain le plus pertinent.

## Taches

- Ajouter entree assistant depuis recherche interurbaine.
- Afficher suggestions, score et explication courte.
- Gerer fallback si IA indisponible.
- Mesurer clics et utilisation sans donnees sensibles.

## Criteres d'acceptation

- Une recommandation renvoie vers le detail annonce.
- L'explication est comprehensible et courte.
- Une indisponibilite IA ne bloque pas la recherche classique.
- Les evenements d'usage sont envoyes au backend.

## References

- Backlog: MGO-018
- Cahier des charges: F-05

# MOB-WALLET-001 - Wallet mobile, depot Mobile Money et paiement trajet

## Priorite

HIGH

## Delai estime

5d

## Dependances

- CORE-WALLET-001
- CORE-PAY-001
- CORE-PAY-002

## Objectif

En tant qu'utilisateur, je veux consulter mon wallet, le recharger et payer un trajet afin de reduire l'usage des especes.

## Taches

- Afficher solde, devise FCFA et historique mouvements.
- Ajouter parcours depot MTN MoMo, Orange Money ou agregateur.
- Ajouter paiement trajet et recu.
- Gerer solde insuffisant, transaction en attente, echec et expiration.

## Criteres d'acceptation

- Le solde est rafraichi apres depot confirme.
- Un paiement impossible n'altere pas la reservation.
- Le recu est accessible apres paiement.
- Les erreurs paiement sont localisables.

## References

- Backlog: MGO-019, MGO-020, MGO-021
- Cahier des charges: F-08

# MOB-NFC-001 - Paiement NFC hors-ligne mobile

## Priorite

MEDIUM

## Delai estime

5d

## Dependances

- MOB-WALLET-001
- CORE-NFC-001

## Objectif

En tant qu'utilisateur, je veux echanger un paiement NFC sans connexion afin de finaliser un trajet quand le reseau est instable.

## Taches

- Integrer lecture/ecriture NFC Android selon contraintes Flutter.
- Generer et stocker les jetons hors-ligne autorises.
- Afficher transactions en attente de synchronisation.
- Gerer echecs NFC, appareil incompatible et jeton expire.

## Criteres d'acceptation

- Un appareil compatible peut emettre ou recevoir un jeton NFC.
- Les transactions en attente sont clairement signalees.
- Un jeton expire ne peut pas etre utilise.
- Le parcours reste masque si l'appareil ne supporte pas NFC.

## References

- Backlog: MGO-022
- Cahier des charges: F-13

# MOB-BOOKING-001 - Reservation, notifications et suivi de trajet

## Priorite

HIGH

## Delai estime

5d

## Dependances

- MOB-INTER-001
- MOB-WALLET-001
- CORE-NOTIF-001
- CORE-TRACK-001

## Objectif

En tant que passager, je veux bloquer ma place, recevoir les notifications et suivre le trajet afin de savoir quoi faire a chaque etape.

## Taches

- Afficher blocage de place, expiration et statut de reservation.
- Integrer notifications push et preferences.
- Afficher statuts confirme, en approche, a bord, termine, annule.
- Afficher position conducteur pendant trajet actif.

## Criteres d'acceptation

- Le passager voit son statut a tout moment.
- Le transporteur est notifie d'une confirmation.
- La position conducteur est visible uniquement sur trajet actif.
- La fin de trajet ouvre recu et post-trajet.

## References

- Backlog: MGO-023, MGO-024, MGO-025
- Cahier des charges: F-10

# MOB-OFFLINE-001 - Cartes telechargeables, cache et synchronisation

## Priorite

MEDIUM

## Delai estime

5d

## Dependances

- MOB-MAP-001
- MOB-NFC-001
- CORE-OFFLINE-001

## Objectif

En tant qu'utilisateur, je veux utiliser les donnees essentielles hors connexion et synchroniser mes actions au retour du reseau.

## Taches

- Permettre telechargement de zones de carte.
- Mettre en cache trajets, reservations et donnees utiles.
- Afficher indicateur offline et file d'operations.
- Synchroniser automatiquement au retour de connectivite.

## Criteres d'acceptation

- Les cartes selectionnees restent disponibles hors ligne.
- Les actions hors connexion sont empilees localement.
- Les conflits sont expliques a l'utilisateur.
- Le cache peut etre nettoye depuis les parametres.

## References

- Backlog: MGO-026, MGO-027
- Cahier des charges: F-13

# MOB-GAME-001 - Mini-jeu hors-ligne

## Priorite

LOW

## Delai estime

2d

## Dependances

- MOB-OFFLINE-001

## Objectif

En tant qu'utilisateur, je veux acceder a un mini-jeu hors-ligne afin de conserver une experience pendant les indisponibilites reseau.

## Taches

- Ajouter mini-jeu leger disponible depuis l'etat hors-ligne.
- Garantir aucune dependance serveur ni donnee sensible.
- Ajouter parametre de desactivation.
- Verifier poids application et temps de chargement.

## Criteres d'acceptation

- Le mini-jeu fonctionne sans reseau.
- Il ne bloque aucun parcours transport.
- Il peut etre desactive sans impact applicatif.
- Le poids ajoute reste documente.

## References

- Backlog: MGO-028
- Cahier des charges: F-13

# MOB-POST-001 - Notation, historique et support mobile

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- MOB-BOOKING-001
- CORE-POST-001

## Objectif

En tant qu'utilisateur mobile, je veux noter apres trajet, consulter mon historique et contacter le support afin de suivre mes deplacements et signaler les problemes.

## Taches

- Ajouter notation etoiles et commentaire apres trajet termine.
- Ajouter historique trajets passes/a venir avec filtres date et statut.
- Afficher recus et statut paiement.
- Ajouter formulaire support categorie, priorite et message.

## Criteres d'acceptation

- La notation n'apparait qu'apres trajet termine.
- L'historique se filtre et ouvre le detail trajet.
- Les recus et montants sont visibles.
- Le support affiche un accuse de reception.

## References

- Backlog: MGO-029, MGO-030, MGO-031
- Cahier des charges: F-11, F-12, F-14

