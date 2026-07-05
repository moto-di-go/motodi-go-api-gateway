# Tickets - motodi-go-admin-dashboard

## Labels communs

`repo:motodi-go-admin-dashboard`, `project:moto-di-go`

# ADMIN-AUTH-001 - Shell back-office, authentification admin et RBAC

## Priorite

HIGH

## Delai estime

3d

## Dependances

- GATE-AUTH-001
- CORE-AUTH-001
- CORE-PROF-001

## Objectif

En tant qu'administrateur ou agence, je veux acceder a un back-office securise afin d'exploiter la plateforme selon mes permissions.

## Taches

- Creer application Next.js avec layout, navigation et session admin.
- Integrer login, refresh session et logout.
- Appliquer RBAC pour administrateur plateforme, support et agence.
- Ajouter gestion erreurs, loading et pages non autorisees.

## Criteres d'acceptation

- Un utilisateur non autorise ne peut pas acceder au dashboard.
- Les menus changent selon le role.
- Les sessions expirees redirigent vers login.
- Les erreurs API sont affichees sans fuite technique.

## References

- Backlog: MGO-032
- Cahier des charges: section 5.2

# ADMIN-KYC-001 - Validation documentaire et profils verifies

## Priorite

HIGH

## Delai estime

5d

## Dependances

- ADMIN-AUTH-001
- CORE-KYC-002

## Objectif

En tant qu'administrateur, je veux examiner, valider ou rejeter les documents afin de garantir la fiabilite des conducteurs et transporteurs.

## Taches

- Creer file dossiers en attente avec filtres profil/statut.
- Afficher documents, informations profil et historique.
- Ajouter actions valider/rejeter avec motif obligatoire.
- Ajouter audit visuel des decisions.

## Criteres d'acceptation

- Les dossiers en attente sont listes et filtrables.
- Une decision est impossible sans permission.
- Un rejet exige un motif.
- L'utilisateur est notifie apres decision.

## References

- Backlog: MGO-009
- Cahier des charges: F-02, F-03

# ADMIN-OPS-001 - Pilotage utilisateurs, annonces, reservations et paiements

## Priorite

HIGH

## Delai estime

5d

## Dependances

- ADMIN-AUTH-001
- CORE-INTER-001
- CORE-BOOK-001
- CORE-PAY-001

## Objectif

En tant qu'equipe support, je veux piloter utilisateurs, annonces, reservations et transactions afin d'exploiter la plateforme au quotidien.

## Taches

- Ajouter liste et detail utilisateurs/profils.
- Ajouter consultation annonces et reservations par statut.
- Ajouter consultation transactions wallet et reconciliation.
- Ajouter recherche, filtres et export CSV selon permissions.

## Criteres d'acceptation

- Les utilisateurs et profils sont consultables par statut.
- Les annonces et reservations sont filtrables.
- Les transactions affichent reference, montant, statut et date.
- Les exports respectent les permissions et masquent les donnees sensibles.

## References

- Backlog: MGO-032
- Cahier des charges: F-04, F-08, F-10

# ADMIN-PRICING-001 - Regles tarifaires et couches SIG administrables

## Priorite

HIGH

## Delai estime

5d

## Dependances

- ADMIN-AUTH-001
- CORE-PRICE-001

## Objectif

En tant qu'administrateur, je veux parametrer les regles tarifaires et couches SIG afin de standardiser les prix et informer les usagers sur les routes.

## Taches

- Creer CRUD regles tarifaires par zone, creneau et coefficient.
- Ajouter publication couches trafic, travaux, routes degradees et voies alternatives.
- Ajouter previsualisation carte.
- Journaliser modifications et auteur.

## Criteres d'acceptation

- Une regle tarifaire modifiee est auditable.
- Les regles actives sont consommees par le calculateur.
- Les couches SIG sont visibles dans le dashboard et exposables au mobile.
- Les erreurs de geometrie ou donnees invalides sont bloquees.

## References

- Backlog: MGO-012, MGO-013, MGO-032
- Cahier des charges: F-06, F-07

# ADMIN-MOD-001 - Moderation avis, signalements et support

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- ADMIN-AUTH-001
- CORE-POST-001

## Objectif

En tant qu'equipe support, je veux moderer commentaires, signalements et demandes support afin de maintenir la confiance sur la plateforme.

## Taches

- Lister avis/commentaires signales.
- Ajouter actions masquer, restaurer ou escalader.
- Lister tickets support par categorie, priorite et statut.
- Ajouter reponse ou changement de statut support.

## Criteres d'acceptation

- Un commentaire signale est visible en moderation.
- Les actions de moderation sont tracees.
- Un ticket support a categorie, priorite et statut.
- Les utilisateurs recoivent un accuse ou changement de statut.

## References

- Backlog: MGO-029, MGO-031, MGO-032
- Cahier des charges: F-11, F-14

# ADMIN-KPI-001 - Tableaux de bord KPI produit, technique et financier

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- ADMIN-AUTH-001
- CORE-ANALYTICS-001
- AI-METRICS-001

## Objectif

En tant que decideur, je veux consulter les KPI produit, techniques et business afin d'evaluer adoption, congestion, fiabilite et revenus.

## Taches

- Afficher MAU, conducteurs verifies actifs, reservations, volume transactions et SLA.
- Ajouter taux utilisation IA et temps moyen synchronisation SIG.
- Ajouter filtres periode et export CSV.
- Masquer ou agreger toute donnee personnelle.

## Criteres d'acceptation

- Les KPI sont affiches par periode.
- L'export CSV correspond aux donnees visibles.
- Les donnees sensibles ne sont pas exposees.
- Les cibles BMC M6/M12 sont comparables aux resultats.

## References

- Backlog: MGO-033
- Cahier des charges: section 8.2
- BMC: MAU, drivers actifs, volume Mobile Money, SLA 99.9%

# ADMIN-AGENCY-001 - Espace agence transport et monetisation SaaS

## Priorite

MEDIUM

## Delai estime

3d

## Dependances

- ADMIN-AUTH-001
- CORE-INTER-001
- CORE-ANALYTICS-001

## Objectif

En tant qu'agence partenaire, je veux gerer mes annonces, reservations et statistiques afin de justifier l'abonnement mensuel agence.

## Taches

- Creer vue agence limitee aux donnees de l'agence.
- Afficher annonces, reservations, taux de remplissage et historique.
- Ajouter indicateur abonnement/forfait agence.
- Preparer restrictions pour offres premium ou priorite affichage.

## Criteres d'acceptation

- Une agence ne voit que ses donnees.
- Les statistiques principales sont disponibles.
- Le forfait agence est representable dans le dashboard.
- Les permissions sont testees.

## References

- Backlog: MGO-015, MGO-032, MGO-033
- BMC: forfait agence transport 10 000 FCFA/mois

