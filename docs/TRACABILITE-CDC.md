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
| RM07 | Une initiative « Terminée » a un avancement de **100 %** ; inversement, atteindre 100 % la clôt **automatiquement depuis n'importe quel état actif** (« À planifier », « En cours », « En pause »), saisie manuelle comme calcul par les étapes — l'« Annulée » reste figée. La clôture n'est **pas un état figé** : rouverrir une étape (ou en ajouter une) fait retomber l'avancement sous 100 % et **réouvre automatiquement** l'initiative, avec l'avancement recalculé (« À planifier » si l'on retombe à 0 %, « En cours » sinon), transition tracée au journal. Toute donnée héritée bloquée à 100 % sous un statut actif est réalignée au démarrage du backend. | `InitiativeService.modifier` (RM07-auto, transition automatique admise hors diagramme), `EtapeService.recalculerAvancement` (RM07-auto et réouverture-auto), `creer` (dérivation), `DataInitializer.alignerAvancements` (alignement au démarrage) — passages journalisés dans l'historique | JUnit `modifier_avancement100_forceTerminee`, `modifier_aPlanifier/…_enPause/…_annulee…`, `EtapeServiceTest` (En pause / À planifier → Terminée + journal, **Réouverture** : étape rouverte → En cours si partiel / À planifier si 0 % + journal, toutes terminées → figé), `creer_avancement100_terminee` — API section 4/6 (`RM07-auto : …`, `RM07 : étape rouverte à 0 % …`) |
| RM17 | Sous pilotage des étapes, une initiative à **0 % n'a pas encore commencé** : son statut est **« À planifier »**, jamais « En cours ». Le retour est automatique lors d'un **recalcul déclenché par les étapes** (création, modification, suppression, réouverture) ou de l'**alignement au démarrage**, et il est journalisé ; un choix manuel explicite (formulaire ou Kanban) reste respecté, de même que l'arrêt délibéré « En pause » et les initiatives sans étapes (statut piloté à la main). | `EtapeService.recalculerAvancement` (retour-auto 0 %), `DataInitializer.alignerAvancements` (alignement au démarrage) | JUnit `EtapeServiceTest.RetourAZero` (En cours → À planifier + journal / En pause conservé / référentiel absent), `modifier_enCours0Etapes_promotionManuelleRespectee` (limite documentée) — API section 6 (`RM17 : « En cours » à 0 % …`) |
| RM08 | Une **étape** appartient à une seule initiative. | `Etape.initiative` (`@ManyToOne`), FK `etape.id_initiative` | API (création d'étape sur initiative) |
| RM09 | Un **jalon** appartient à une seule initiative. | `Jalon.initiative` (`@ManyToOne`), FK `jalon.id_initiative` | API (création de jalon) |
| RM10 | Seuls les utilisateurs **autorisés** peuvent modifier les données. | `SecurityConfig` + permissions (`INITIATIVE_*`, `ETAPE_GERER`, `JALON_GERER`, `UTILISATEUR_GERER`, `ROLE_GERER`, `IA_UTILISER`) | API section 7 (403/401) |
| RM11 | Le **code** d'une initiative est **unique**. | `InitiativeService.validerCodeUnique` + `UNIQUE` SQL | JUnit `creer_codeDuplique` — API `Code dupliqué → 400` |
| RM12 | Une initiative possède **un seul statut et une seule priorité**. | `initiative.id_statut`, `initiative.id_priorite` (FK non nullables) | API création/lecture |
| RM13 | Une initiative peut comporter **0..N étapes et 0..N jalons**. | `Initiative.etapes`, `Initiative.jalons` (`@OneToMany`), FK `ON DELETE CASCADE` | API (listes étapes/jalons, init. de démo 5 étapes) |
| RM14 | L'**archivage n'est pas un statut** et ne supprime pas l'initiative. | Champ `archive` (booléen), `InitiativeService.archiver` (réservé à Terminée/Annulée), pas de statut « Archivée » dans le référentiel | JUnit `Archivage` — API `Ré-archivage idempotent`, `Modification après archivage → 400` |
| RM15 | La **date de réalisation** d'un jalon est vide tant qu'il n'est pas **atteint**. | `JalonService` (RM15) → `dateRealisation` auto/vide selon le statut | API `Jalon non atteint → date réalisation vide` |
| RM16 | Une initiative est rattachée à **une seule entité**. | `initiative.id_entite` (`@NotNull` + FK) | API création/lecture |
| RM18 | Statut et avancement d'une étape **se déduisent mutuellement** (double sens) : le **statut choisit sa valeur par défaut** (« À faire » 0 %, « En cours » 50 %, « Terminée » 100 % — applicable lorsque le champ n'est pas renseigné, absent ou à 0) ; **inversement, un nombre clairement saisi prime sur le statut** : 1-99 → statut « En cours » avec la valeur conservée, 100 → statut « Terminée ». L'initiative pilotée recalcule (pondération par la durée), et les étapes héritées incohérentes (À faire ≠ 0, Terminée ≠ 100, En cours à 0) sont réalignées au démarrage. | `EtapeService.harmoniser` (création/modification), `CalculeurAvancement.progression` (calcul), `DataInitializer.avancementCoherent` (alignement au démarrage) ; IHM : `changerStatutEtape` (statut → valeur) **et** `changerAvancementEtape` (valeur → statut) tiennent les deux sens à l'écran | JUnit `EtapeServiceTest.CoherenceStatutAvancement` (défauts 0/50/100, 87 → « En cours », 100 → « Terminée ») — API section 6 (`RM18 : …`) |

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

## 4quater. P3 — Historique des statuts (journal global)

| Fonctionnalité (CDC) | Mise en œuvre | Validation |
|---|---|---|
| **Journal des transitions** : tout changement de statut inscrit dans l'historique (CDC §7.2), écrit dans la même transaction que la modification | `HistoriqueStatutService.enregistrer` (appelé par `InitiativeService`, `EtapeService`, `DataInitializer`), table `historique_statut` | JUnit `HistoriqueStatutServiceTest` + API section 12 (création → transition manuelle → passage RM07-auto : 3 lignes) |
| **Origine du changement** : colonne `historique_statut.automatique` (manuel / moteur de règles : RM07-auto, réouverture, RM17, alignement de démarrage) ; migration douce idempotente + re-classement rétroactif depuis le motif enregistré | Entité `HistoriqueStatut.automatique`, surcharge `enregistrer(..., boolean)`, `database/schema.sql` (ALTER gardé par `information_schema`) | JUnit (origine manuelle/automatique) + API (`automatique = false` sur création/transition choisie, `= true` sur RM07) |
| **Page dédiée** `/historique` : table (code, nom, ancien/nouveau statut, utilisateur, date-heure, origine), recherche code/nom (insensible casse/accents), filtres statut/utilisateur/période, tri, pagination, export CSV | `HistoriquePage.jsx`, `GET /api/historique-statuts` (`HistoriqueStatutController` + `HistoriqueStatutRepository.rechercher/compter`, en-tête `X-Total-Count`), route/menu gated `INITIATIVE_CONSULTER` | API section 12 (recherche, filtres, pagination, tri, 400 paramètres) + build Vite |
| **Résumé sur la fiche** : 5 derniers changements + bouton « Voir tout l'historique » ouvrant la page filtrée (`?initiative=id`, pastille débrayable) | `InitiativeDetailPage.jsx` (`slice(0, 5)`), lien `Link`, pastille `filtre-pastille` | Build Vite + API (champ `initiative` sur chaque ligne) |
| **Lecture + permissions existantes** : lecture ouverte à `INITIATIVE_CONSULTER`, aucune création/édition possible, aucun nouveau droit | `SecurityConfig` : `GET /api/historique-statuts/**` → `INITIATIVE_CONSULTER` ; aucun `@PostMapping/@PutMapping` (POST → 405) | JUnit `SecuriteApiTest` (401/403/200, POST → 405) + API (anonyme 401, visiteur 200) |
| **Suppression d'une entrée (admin)** : nettoyage d'une ligne erronée depuis la colonne « Actions » avec confirmation ; réservée aux administrateurs | `DELETE /api/historique-statuts/{id}` (`HistoriqueStatutController.supprimer`, 204 / 404 si absent), `SecurityConfig` : `DELETE` → `UTILISATEUR_GERER` (droit existant) ; bouton `btn-danger` conditionné à `hasPermission('UTILISATEUR_GERER')` | JUnit `SecuriteApiTest` (anonyme 401, consultation seule 403, admin 204) + `HistoriqueStatutServiceTest` (suppression / 404) + API section 12 (401, 403, 404, 204 + total −1) |

## 4quinquies. Améliorations — score de santé et durcissement

| Fonctionnalité | Mise en œuvre | Validation |
|---|---|---|
| **Score de santé automatique** : feu vert / vigilance / à risque, calculé depuis les données déjà suivies — date butoir dépassée (rouge), ≥ 2 jalons en retard (rouge), 1 jalon (orange), écart planning/avancement ≥ 30 pts (rouge) / ≥ 15 pts (orange), terminée (vert) ; archivée = sans objet. Motif i18n affiché (« Date butoir dépassée de N jours », « X % du temps écoulé pour Y % d'avancement »…) | `CalculeurSante` (règles pures déterministes), `SanteDto(niveau, raison, parametres)` dans chaque `InitiativeResponse` de lecture (liste, fiche, surveillance dashboard) ; jalons chargés en lot (`JalonRepository.chercherParInitiatives`, une requête, pas de N+1) ; `SanteBadge.jsx` + `i18n/locales/sante.js` (fr/en/ar/es) | JUnit `CalculeurSanteTest` (13 tests : chaque règle, priorité du rouge, jalons atteints ignorés, chargement en lot) — API section 3 (niveau + motif sur chaque ligne, MOB-SUI-003 → ROUGE `finDepassee`, fiche enrichie, surveillance enrichie, écritures → `null`) |
| **Assistant IA « Demande à ton portefeuille »** : chat et résumé d'initiative branchés sur l'API Anthropic — **grounding strict** (contexte reconstruit à chaque requête depuis la base : initiatives non archivées + statut, avancement, santé, budget, responsable ; prompt système interdisant d'inventer, réponses dans la langue choisie fr/en/ar/es), permission dédiée `IA_UTILISER` (17ᵉ, ADMIN + RESPONSABLE, jamais VISITEUR), **kill switch** `IA_ACTIF=false`, clé absente → 503 explicite + état « indisponible » côté interface, **journalisation intégrale** (`journal_ia` : auteur, demande, réponse, statut OK/ERREUR/NON_DISPONIBLE, durée — écritures hors transaction pour survivre à l'exception) | `AssistantIaController` (`/api/ia/assistant`, `/ia/resume/{id}`, `/ia/statut`, `/ia/journal`), `AssistantIaService` (contexte + prompt système + journal), `ClientIa` (RestClient Anthropic, timeouts, traduction des erreurs HTTP en messages actionnables), `JournalIa` + `journal_ia`, `SecurityConfig` (403 sans `IA_UTILISER`, journal admin), `IaIndisponibleException` → 503 — front : `AssistantPage.jsx` (`/assistant`, chat + suggestions + état indisponible), bouton « Résumé IA » (`InitiativeDetailPage`), `i18n/locales/assistant.js` (fr/en/ar/es) | JUnit `AssistantIaServiceTest` (8 tests : disponibilité, kill switch + journal, contexte réel, archivées exclues, échec modèle → ERREUR, résumé avec étapes/jalons, 404) + `SecuriteApiTest.AssistantIa` (4 tests : statut 401/200, 403/200 assistant et résumé, journal admin) — API section 13 (8 contrôles : permission, disponibilité, 403/404, journal) |
| **Secrets non codés en dur** : compte administrateur initial paramétré par environnement | `app.admin.email/password` (application.yml) lus par `DataInitializer` via `ADMIN_EMAIL` / `ADMIN_PASSWORD` ; plus de mot de passe en dur dans le code source ; transmis par `docker-compose.yml` | Démarrage backend ( création du compte admin avec les variables d'environnement ) + suite API (login admin inchangé) |
| **JWT** : `.env` livré avec une clé aléatoire réelle (48 octets tirés au sort) ; `.env.example` n'expose plus la valeur par défaut connue | `VerificationSecrets` (refus hors profil dev), `package.ps1` exclut déjà `.env` des archives de remise | Démarrage du backend Docker (refus sans secret) + login/requêtes avec le nouveau secret |
| **CSP (Content-Security-Policy)** : `default-src 'self'`, `script-src 'self'` (aucun script inline), `font-src 'self'` (polices embarquées), `style-src` limité aux styles inline React ; en-têtes repris dans `location /assets/` (règle d'héritage `add_header` de nginx) | `frontend/nginx.conf` | Console navigateur : 0 violation sur login, dashboard, liste et fiche ; en-tête présent sur `/` et `/assets/*` (vérifié via requête HTTP) |
| **Identité visuelle IT6** : variables `--jira-*` renommées `--accent-*` (51 occurrences), plus aucune mention Jira/Atlassian dans la feuille de style, palette bordeaux IT6 assumée ; motif zellige (étoile à huit branches, SVG inline, RTL-safe) sur le login et l'en-tête | `styles.css`, `MotifZellige.jsx`, `LoginPage.jsx`, `Layout.jsx` | Build Vite + console navigateur (0 violation CSP : SVG inline, aucune image externe) + DOM (`svg pattern` sur login et topbar) |

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
- **P3 (historique des statuts)** : 30 scénarios supplémentaires (section 12 de
  `tests-api.ps1`) : origine manuelle/automatique des lignes, page globale,
  recherche, filtres statut/utilisateur/période, pagination, tri, validation des
  paramètres, permissions, lecture seule (POST → 405) et suppression
  administrative (401/403/404/204 + total décrémenté), purge totale refusée hors
  administrateur (401/403 pour anonyme, visiteur et responsable).
- **P4 (recherche et suppression)** : les trois écrans de listes disposent
  d'une barre de recherche ; la suppression suit les droits existants —
  demandes (`DEMANDE_EVALUER`, 400 si déjà transformée, **sélection multiple
  en lot** : cases à cocher + barre « Supprimer (n) » avec une seule
  confirmation), initiatives
  uniquement archivées (`INITIATIVE_ARCHIVER`), purge du journal réservée à
  `UTILISATEUR_GERER`. Couvert par 15 scénarios fonctionnels (sections 11 et
  12), 11 tests JUnit (recherche `%…%`, refus 400/404, règles d'URL) et le
  e2e navigateur (sélection de 2 demandes → suppression en lot → reste
  démo intacte).
- **P5 (comptes et journal des accès)** : les comptes se suppriment
  définitivement (bouton masqué sur son propre compte), le serveur refusant
  en 400 son propre compte, le dernier administrateur actif et un compte qui
  porte encore des demandes ; chaque tentative de connexion — réussie ou
  échouée — est tracée dans `historique_connexion` (horodatage, e-mail saisi,
  IP, motif) et consultée depuis `/historique-connexions` avec recherche,
  filtres, suppression de ligne et purge totale, **réservées aux
  administrateurs** (`UTILISATEUR_GERER`, tous verbes — aucun nouveau droit).
  L'écriture du journal est en transaction propre et best-effort : elle ne
  peut ni faire échouer une connexion ni masquer l'erreur d'authentification
  d'origine. Couvert par 28 scénarios fonctionnels (sections 14 et 15) et
  20 tests JUnit (`UtilisateurServiceTest`, `HistoriqueConnexionServiceTest`,
  2 règles d'URL supplémentaires dans `SecuriteApiTest`).
- **Score de santé** : 5 scénarios fonctionnels (section 3 de `tests-api.ps1`) et
  13 tests JUnit (`CalculeurSanteTest`) : niveau + motif sur liste/fiche/surveillance,
  rouge sur initiative en retard, `null` sur les écritures.
- **Bilan chiffré** : **169 tests JUnit** et **204 scénarios fonctionnels**,
  0 échec (dernière campagne). Deux scénarios contrôlent les KPI d'accueil
  personnalisé : `jalonsSemaine` (jalons non atteints du jour au dimanche
  courant) et `initiativesARisque` (santé ORANGE ou ROUGE recalculée depuis la
  liste des initiatives).
- Les règles structurales (RM02/08/09/12/13/16) sont garanties par le schéma
  (clés étrangères, contraintes de non-null) et exercées par les tests de l'API.
- Les KPI du CDC sont tous calculés par l'API (aucune valeur codée en dur),
  contrôlés par recalculation indépendante dans `tests-api.ps1`.

> Document généré à partir du code source et du cahier des charges ; à faire
> valider par l'encadrant lors de la soutenance.