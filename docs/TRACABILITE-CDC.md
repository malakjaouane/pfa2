# Matrice de traçabilité — Cahier des charges → Code → Tests

**Document source :** `docs/CAHIER_DES_CHARGES_PFA_IT6_VERSION_PROFESSIONNELLE_CORRIGEE.docx`
**Vérification :** chaque exigence du cahier des charges est reliée à son
implémentation (fichier/fonction) et à sa validation (test unitaire JUnit et/ou
test fonctionnel `tests-api.ps1`).

---

## 1. Règles métier RM01 → RM16

| # | Exigence (CDC) | Mise en œuvre | Validation |
|---|---|---|---|
| RM01 | Une initiative doit posséder un **nom**. | `InitiativeRequest.nom` (`@NotBlank`) + `InitiativeService.validerReglesMetier` | JUnit — API `Nom vide → 400` |
| RM02 | Une initiative doit être associée à un **statut**. | `InitiativeRequest.idStatut` (`@NotNull`) ; FK `initiative.id_statut` | JUnit (dérivation du statut) — API création |
| RM03 | Une initiative a **zéro ou un seul responsable**. | `initiative.id_responsable` nullable, FK `ON DELETE SET NULL`, `resoudreResponsable` (utilisateur actif requis) | JUnit `responsable inactif` / `introuvable` |
| RM04 | La **date de fin prévue** ≥ **date de début**. | `InitiativeService.validerReglesMetier` + `CHECK` en `schema.sql` | JUnit `creer_dateFinAvantDebut` — API `Fin < début → 400` |
| RM05 | Le **budget** ≥ 0. | `@DecimalMin("0.0")` + contrôle service | JUnit `creer_budgetNegatif` — API `Budget négatif → 400` |
| RM06 | L'**avancement** ∈ [0, 100]. | `@Min/@Max` + contrôle service + `CHECK` SQL | JUnit `creer_avancementHorsBornes` — API `Avancement > 100 → 400` |
| RM07 | Une initiative « Terminée » a un avancement de **100 %** (+ auto-bascule à 100 %). | `InitiativeService.modifier` (RM07-auto), `creer` (dérivation) | JUnit `modifier_avancement100_forceTerminee`, `modifier_termineeSans100`, `creer_avancement100_terminee` — API section 4/6 |
| RM08 | Une **étape** appartient à une seule initiative. | `Etape.initiative` (`@ManyToOne`), FK `etape.id_initiative` | API (création d'étape sur initiative) |
| RM09 | Un **jalon** appartient à une seule initiative. | `Jalon.initiative` (`@ManyToOne`), FK `jalon.id_initiative` | API (création de jalon) |
| RM10 | Seuls les utilisateurs **autorisés** peuvent modifier les données. | `SecurityConfig` + permissions (`INITIATIVE_*`, `ETAPE_GERER`, `JALON_GERER`, `UTILISATEUR_GERER`, `ROLE_GERER`) | API section 7 (403/401) |
| RM11 | Le **code** d'une initiative est **unique**. | `InitiativeService.validerCodeUnique` + `UNIQUE` SQL | JUnit `creer_codeDuplique` — API `Code dupliqué → 400` |
| RM12 | Une initiative possède **un seul statut et une seule priorité**. | `initiative.id_statut`, `initiative.id_priorite` (FK non nullables) | API création/lecture |
| RM13 | Une initiative peut comporter **0..N étapes et 0..N jalons**. | `Initiative.etapes`, `Initiative.jalons` (`@OneToMany`), FK `ON DELETE CASCADE` | API (listes étapes/jalons, init. de démo 5 étapes) |
| RM14 | L'**archivage n'est pas un statut** et ne supprime pas l'initiative. | Champ `archive` (booléen), `InitiativeService.archiver` (réservé à Terminée/Annulée), pas de statut « Archivée » dans le référentiel | JUnit `Archivage` — API `Ré-archivage idempotent`, `Modification après archivage → 400` |
| RM15 | La **date de réalisation** d'un jalon est vide tant qu'il n'est pas **atteint**. | `JalonService` (RM15) → `dateRealisation` auto/vide selon le statut | API `Jalon non atteint → date réalisation vide` |
| RM16 | Une initiative est rattachée à **une seule entité**. | `initiative.id_entite` (`@NotNull` + FK) | API création/lecture |

## 2. Suivi de l'avancement et « en retard »

| Règle CDC | Mise en œuvre | Validation |
|---|---|---|
| Avancement = pourcentage ; barre de progression | `ProgressBar.jsx`, `avancement` dans `InitiativeResponse` | Vérification UI + API |
| **En retard** : non archivée, échéance dépassée, statut ≠ Terminée, avancement < 100 % | `InitiativeMapper.enRetard` + `InitiativeRepository.countEnRetard/findEnRetard` | JUnit `InitiativeMapperTest` (6 cas) — API KPI |

## 3. Dashboard et KPI

| KPI (CDC) | Calcul | Validation |
|---|---|---|
| Total initiatives (non archivées) | `countByArchiveFalse` | API (recalcul indépendant) |
| En cours / Terminées / En pause | `countByArchiveFalseAndStatutLibelle` | API |
| En retard | `countEnRetard` | API (`MOB-SUI-003` listée) |
| Budget total | `sumBudgetNonArchive` | API |
| Avancement moyen | `avgAvancementNonArchive` (arrondi 1 décimale) | JUnit `DashboardServiceTest` |
| Répartitions (statut, priorité, **entité**, **responsable**, **objectif**) | `countBy*NonArchive` + `countByObjectifNonArchive` | API (somme des groupes = total) |
| Liste des initiatives en retard | `findEnRetard` | API |

## 4. Recherche et filtres combinés

| Filtre CDC | Implémentation |
|---|---|
| Statut, Entité, Responsable, Période, Priorité, **Objectif stratégique** | `InitiativeFilterRequest` + `InitiativeSpecifications` (composition AND, recherche textuelle) |
| Combinaison possible | `sort` + `Specification` ; vérifié par l'API (`statut+responsable+priorite`, `objectif`) |

## 4bis. P1 — Kanban et Roadmap

| Fonctionnalité (CDC) | Mise en œuvre | Validation |
|---|---|---|
| **Vue Kanban** : 5 colonnes par statut métier, navigation vers le détail | `KanbanBoard.jsx`, bascule Tableau/Kanban dans `InitiativesPage.jsx` (`/initiatives`) | Build Vite + API (données réelles depuis `/initiatives`) |
| Déplacements par glisser-déposer (changement de statut) | `KanbanBoard.jsx` (glisser-déposer), `PUT /initiatives/{id}/statut` (`InitiativeController.changerStatut`), mêmes règles de cycle de vie que le formulaire | `SecuriteApiTest` (changement de statut), tests API (transitions) |
| **Roadmap** : timeline mensuelle calculée depuis les dates réelles, barres colorées par statut, ligne « aujourd'hui », initiatives non planifiées listées sous le graphique | `RoadmapPage.jsx` (`/roadmap`), calcul 100 % frontend (`moisEntre`, positions en % de la période) | Build Vite + API (données réelles) |

## 4ter. P2 — Objectifs stratégiques et Demandes

| Fonctionnalité (CDC) | Mise en œuvre | Validation |
|---|---|---|
| **Objectifs stratégiques** : référentiel (code unique, libellé, description), page de gestion, rattachement d'une initiative (`initiative.id_objectif`), filtre liste, KPI dashboard, select formulaire, badge fiche | `ObjectifService/ObjectifController`, `ObjectifStrategiqueRepository`, `InitiativeModele.objectif`, `ObjectifsPage.jsx`, `InitiativeFormPage.jsx`, `InitiativeDetailPage.jsx`, `DashboardPage.jsx` | JUnit (mapper) + API section 10 (`GET/POST/PUT /objectifs`, code dupliqué 400, 403 visiteur, filtre `objectif=1`) |
| **Demandes d'initiatives (workflow SPM)** : Brouillon → Soumise → En évaluation → Acceptée/Refusée (motif facultatif) | `DemandeService/DemandeController`, `DemandeInitiative` + `StatutDemande`, transitions à états contrôlés (400 si précédent absent) | JUnit `DemandeServiceTest` (14 tests) + API section 11 (transitions validées/interdites, refus avec/sans motif, transformation) |
| **Transformation en initiative sans doublon** : code auto `DEM-%06d`, statut « À planifier », budget/avancement 0, `id_initiative_liee` UNIQUE | `DemandeService.transformer` + `InitiativeService.creerEnEntite` | JUnit `Transformation en initiative` (3 tests) + API (`seconde transformation → 400`) |
| **Permissions** : `DEMANDE_SOUMETTRE` (soumettre) et `DEMANDE_EVALUER` (évaluer/accepter/refuser/transformer) ; lister exige l'une des deux | `SecurityConfig`, `data.sql` (permissions 14/15, role_permission) | API (`visiteur → 403`, responsable autorisé → 200) |

## 5. Acteurs et permissions

| Rôle (CDC) | Permissions (15 / 12 / 3) | Mise en œuvre |
|---|---|---|
| Administrateur | Toutes (1→15, dont `DEMANDE_SOUMETTRE`, `DEMANDE_EVALUER`) | `data.sql` (role_permission) + `SecurityConfig` |
| Responsable | Consultation, gestion initiatives/étapes/jalons, dashboard, filtres, `DEMANDE_SOUMETTRE`/`DEMANDE_EVALUER` | `data.sql` + `SecurityConfig` |
| Visiteur | Consultation, dashboard, filtres | `data.sql` + `SecurityConfig` |

## 6. Bilan de couverture

- **RM01 → RM16 : 16/16 couvertes** par au moins un test (unitaire et/ou fonctionnel).
- **P1/P2** : 15 scénarios fonctionnels supplémentaires (sections 10 et 11 de
  `tests-api.ps1`) : référentiel objectifs, filtre, workflow complet des demandes,
  refus avec/sans motif, transformation sans doublon, permissions.
- Les règles structurales (RM02/08/09/12/13/16) sont garanties par le schéma
  (clés étrangères, contraintes de non-null) et exercées par les tests de l'API.
- Les KPI du CDC sont tous calculés par l'API (aucune valeur codée en dur),
  contrôlés par recalculation indépendante dans `tests-api.ps1`.

> Document généré à partir du code source et du cahier des charges ; à faire
> valider par l'encadrant lors de la soutenance.