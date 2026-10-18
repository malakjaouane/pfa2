# Rapport final — Portefeuille d'initiatives digitales IT6 Consulting

**Projet :** outil de gestion d'un portefeuille d'initiatives digitales
**Client / structure :** IT6 Consulting
**Dépôt :** `C:\Users\t14\Downloads\pfa`
**Stack :** Spring Boot 3.5.16 (Java 17) · React 18 / Vite 5 · MySQL 8.4 · JWT/BCrypt

---

## 1. Contexte et objectifs

L'application permet de piloter un portefeuille d'initiatives digitales :
suivi des initiatives, de leurs étapes et jalons, calcul de KPI (tableau de bord),
et administration des utilisateurs, rôles et permissions.

L'objectif de l'intervention était de **professionnaliser l'existant** sans le
réécrire, en garantissant la cohérence du fil conducteur
**Sujet → Cahier des charges → UML → Architecture → Base de données → API → Frontend**,
et en respectant les contraintes suivantes :

- conserver la stack et les fonctionnalités existantes ;
- calculer les KPI depuis la base réelle (aucune donnée codée en dur) ;
- appliquer la palette imposée ;
- fournir les diagrammes UML corrigés (Mermaid + PlantUML) ;
- livrer un rapport final structuré.

---

## 2. Étape 1 — Audit initial

Constats de l'audit (dette technique et incohérences CDC ↔ application) :

| # | Incohérence constatée | Impact | Statut |
|---|---|---|---|
| A1 | Double source SQL (schéma recopié) → risque de divergence | Élevé | Corrigé (source unique `database/`) |
| A2 | Création d'initiative imposant le statut envoyé par le client | Rejet 400 incohérent avec le formulaire | Corrigé (statut dérivé) |
| A3 | KPI partiellement codés/fictifs | Dashboard non fiable | Corrigé (calculs en base) |
| A4 | Secrets (mot de passe DB, clé JWT) en clair non externalisés | Sécurité | Corrigé (variables d'environnement) |
| A5 | Route API inconnue renvoyant une page HTML | Contrat API | Corrigé (404 JSON) |
| A6 | Palette non conforme / couleurs dispersées | Charte graphique | Corrigé (variables CSS) |
| A7 | Suite de tests API fragile (encodage, valeurs figées) | Non rejouable | Corrigé (idempotente) |
| A8 | Absence de tests backend automatisés | Régressions | Corrigé (JUnit) |
| A9 | Aucune documentation de lancement | Reprise | Corrigé (README + `.env.example`) |

Un plan de correction par phases (1 → 6) a été établi à partir de cet audit.

---

## 3. Étape 2 — Architecture du projet

Architecture en trois couches, chaque responsabilité isolée :

- **Backend** (`backend/`) — API REST Spring Boot :
  `controller` (endpoints) → `service` (règles métier) → `repository` (JPA) ;
  `dto` et `mapper` pour découpler le modèle de persistance du contrat d'API ;
  `specification` pour les filtres dynamiques ; `security` (filtre JWT) ;
  `exception` (gestion centralisée des erreurs).
- **Frontend** (`frontend/`) — SPA React/Vite :
  pages, composants réutilisables, contexte d'authentification, service `api`
  (Axios), styles centralisés.
- **Base de données** (`database/`) — **source unique SQL** :
  `schema.sql` (MPD MySQL 8) et `data.sql` (référentiels idempotents), embarqués
  dans le jar via `<resources>` (Maven) puis exécutés par `spring.sql.init`.

Points d'architecture notables :

- une seule source de vérité pour la structure de la base ;
- Hibernate en `ddl-auto: validate` (contrôle de cohérence, pas de génération
  implicite) ;
- KPI calculés par des requêtes d'agrégation JPA dédiées (`InitiativeRepository`).

---

## 4. Étape 3 — Modélisation (UML et MPD)

Les 11 diagrammes de référence sont fournis en images dans `docs/UML/images/`
(voir `docs/UML/README.md`) : cas d'utilisation, classes, BPMN du processus, MCD,
MLD, MPD et cinq séquences (authentification, création d'une initiative,
consultation de la liste, changement de statut, mise à jour d'une étape).

Les sources Mermaid (`docs/UML/diagrammes-mermaid.md`) et PlantUML
(`docs/UML/plantuml/*.puml`) restent disponibles pour les diagrammes
complémentaires (états, activité, composants, déploiement, KPI).

Le modèle de données (MPD) comporte : `role`, `permission`, `role_permission`,
`entite`, `utilisateur`, `statut_initiative`, `priorite`, `initiative`,
`statut_etape`, `etape`, `statut_jalon`, `jalon`, **`objectif_strategique`
(rattachement optionnel via `initiative.id_objectif`), `statut_demande` et
`demande_initiative` (workflow SPM, code `DEM-xxxxxx` lié par
`id_initiative_liee`)**. Le diagramme d'états distingue
explicitement le **cycle de vie des statuts** de l'**archivage**, qui n'est pas
un statut (champ `archive`).

---

## 5. Étape 4 — Développement et corrections

### 5.1 Cohérence métier (cœur du sujet)

- **RM07 — statut « Terminée »** :
  - une initiative « Terminée » doit avoir un avancement de 100 % ;
  - un avancement de 100 % fait passer automatiquement l'initiative à « Terminée ».
- **Création cohérente** : le statut est **dérivé par le domaine**
  (« À planifier », ou « Terminée » si créée à 100 %) ; la valeur de statut
  envoyée par le client est **ignorée**. Cela supprime le rejet 400 déclenché par
  le formulaire (champ statut en lecture seule).
- **Transitions** contrôlées : `À planifier → En cours`,
  `En cours → En pause`, `En pause → En cours`, `En cours → Terminée`,
  et `À planifier / En cours / En pause → Annulée`.
- **Archivage (RM14)** : réservé aux initiatives « Terminée » ou « Annulée ».
- Règles RM04 (dates), RM05 (budget ≥ 0), RM06 (avancement 0–100),
  RM01/RM11 (nom et unicité du code), RM03 (responsable actif facultatif).

### 5.2 Dashboard et KPI

- KPI calculés **depuis la base réelle** : total, en cours, terminées, en pause,
  en retard, budget total, avancement moyen.
- Nouvelles répartitions **par entité** et **par responsable** (backend + 2
  graphiques frontend supplémentaires, portant le total à 4 graphiques Chart.js).
- Palette Chart.js alignée sur la charte.

### 5.3 Frontend

- Refonte de `styles.css` sur la palette imposée, via variables CSS
  (noms de classes conservés → aucune régression fonctionnelle).
- Édition inline des étapes et jalons sur la fiche initiative (vérifiée).

### 5.4 P1 — Kanban et Roadmap

- **Kanban** : bascule « Tableau / Kanban » sur la page Initiatives
  (`KanbanBoard.jsx`), 5 colonnes par statut métier, cartes cliquables vers la
  fiche détail, mise en évidence en rouge des initiatives en retard, compteurs
  par colonne. Le statut se change par glisser-déposer d'une carte
  (`PUT /initiatives/{id}/statut`) ou par le formulaire ; le cycle de vie est
  contrôlé par le backend dans les deux cas.
- **Roadmap** (`RoadmapPage.jsx` via `/roadmap`) : timeline **mensuelle calculée
  côté frontend** depuis les dates réelles (début/fin), barres colorées par
  statut (palette dédiée), **ligne « aujourd'hui »** au marqueur rouge, et
  section « Non planifiées » sous le graphique pour les initiatives sans dates.
  Aucune donnée fictive : tout est dérivé de `GET /initiatives`.

### 5.5 P2 — Objectifs stratégiques et Demandes (workflow SPM)

- **Objectifs stratégiques** : référentiel géré (création/modification par
  `INITIATIVE_MODIFIER`, code unique), page « Objectifs » (`/objectifs`) avec
  décompte des initiatives par objectif **calculé côté frontend** depuis
  `/initiatives`, sélecteur dans le formulaire d'initiative, badge + ligne
  d'information sur la fiche détail, **filtre `objectif=`** dans la liste, et
  **nouveau graphique « Répartition par objectif » + carte « Alignement
  stratégique »** sur le dashboard.
- **Demandes d'initiatives** : module autonome distinct des initiatives
  (`demande_initiative`, code `DEM-%06d`), cycle
  `Brouillon → Soumise → En évaluation → Acceptée/Refusée` à **états contrôlés
  par le backend** (toute transition sans le statut exact = 400), motif de refus
  **facultatif**, page « Demandes » (`/demandes`) avec actions contextuelles par
  statut et badges de statut (palette).
- **Transformation sans doublon** : une demande **Acceptée** peut être
  transformée en initiative (statut « À planifier », budget 0, avancement 0,
  responsable null) ; `id_initiative_liee` est **UNIQUE** → une seconde
  transformation renvoie 400.
- **Permissions** : `DEMANDE_SOUMETTRE` et `DEMANDE_EVALUER` (attribuées à
  ADMIN **et** RESPONSABLE) ; la consultation exige l'une des deux.

### 5.6 API et robustesse

- Réponse **404 JSON** pour toute route inconnue (au lieu d'une page HTML).
- Gestion d'erreurs centralisée : 400/401/403/404/409/500.

---

## 6. Sécurité et configuration

- Secrets externalisés dans `application.yml` via variables d'environnement,
  avec valeurs par défaut de développement :
  `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`,
  `SERVER_PORT`.
- Modèle fourni : **`.env.example`** (le fichier `.env` réel est ignoré par Git).
- Mots de passe hashés **BCrypt** ; jetons **JWT HS256** (expiration 8 h).
- Autorisations par **permission** (ADMIN 15, RESPONSABLE 12, VISITEUR 3) —
  dont les nouvelles `DEMANDE_SOUMETTRE` (14) et `DEMANDE_EVALUER` (15).

---

## 7. Étape 5 — Qualité et tests

### 7.1 Tests unitaires backend (nouveaux)

Créés sous `backend/src/test/java/` (JUnit 5 + Mockito + AssertJ) :

| Suite | Couverture | Tests |
|---|---|---|
| `InitiativeServiceTest` | création, transitions, RM04/05/06/07/14, responsable | 16 |
| `InitiativeMapperTest` | règle « en retard » | 7 |
| `DashboardServiceTest` | agrégation des KPI (dont `parObjectif`), cas base vide | 2 |
| `DemandeServiceTest` | workflow des demandes : création brouillon, transitions validées/interdites, refus avec/sans motif, transformation (OK, doublon, non acceptée) | 14 |
| **Total** | | **39 (0 échec)** |

### 7.2 Tests fonctionnels de l'API

`database/tests-api.ps1` — suite **idempotente** (codes/emails uniques par
exécution). Les KPI sont comparés à un **recalcul indépendant** à partir de la
liste réelle des initiatives (plus aucune valeur codée en dur).

Couverture : authentification, KPI, filtres, règles métier, cycle de vie,
autorisations Spring Security, utilisateurs et rôles, **objectifs
stratégiques (section 10)** et **workflow des demandes (section 11)**.

**Résultat : 102 réussis / 0 échoué.**

### 7.3 Builds

| Vérification | Résultat |
|---|---|
| `mvn test` | 39 tests, 0 échec |
| `tests-api.ps1` | 102 réussis, 0 échoué |
| `npm run build` | 111 modules transformés, build OK |

---

## 8. Étape 6 — Finalisation et exploitation

- **Documentation** : `README.md` (installation, lancement, comptes de démo,
  configuration, tests, charte) et `.env.example`.
- **Script de nettoyage** : `database/cleanup-test-data.sql` purge les résidus de
  tests (initiatives `TEST*` archivées, initiatives issues de demandes de test
  `DEM-*`, demandes `TEST-DEM*`, objectifs `TEST-OBJ*`, comptes `test*@it6.ma`)
  sans jamais toucher aux données réelles.
- **Nettoyage effectué** : base ramenée à son état de référence
  (5 initiatives de démonstration, 3 comptes), aucun résidu de test.
- **Scripts d'exploitation** : `database/init-mysql.ps1`,
  `database/start-mysql.ps1` (démarrage MySQL local), `database/tests-api.ps1`.
- **Livrables documentaires** : `docs/TRACABILITE-CDC.md` (matrice
  CDC ↔ code ↔ tests), `docs/API.md` (référence REST), le cahier des charges
  (copie dans `docs/`), et `.github/workflows/ci.yml` pour l'intégration continue.

---

## 9. Vérifications réalisées (preuves)

- **Palette** lue dans le navigateur (CSS calculé) :
  `--ice-cold #A0D2EB`, `--freeze-purple #E5EAF5`, `--medium-purple #D0BDF4`,
  `--purple-pain #8458B3`, `--heavy-purple #A28089`,
  ainsi que `--primaire-700 #5E3A8C` et `--primaire-900 #3F2A5C` ;
  fond de page `rgb(229, 234, 245)` = Freeze Purple.
- **Dashboard** (après nettoyage de la base), KPI affichés :
  total **5**, en cours **1**, terminées **1**, en pause **1**, en retard **1**,
  budget total **1 070 000 MAD**, avancement moyen **39 %**, **5 graphiques**,
  table des retards contenant `MOB-SUI-003` — cohérents avec les données réelles.
- **Répartition par objectif** (dashboard) : 4 groupes — « Non rattaché » (2 :
  `MOB-SUI-003`, `RESP-204122`), « Améliorer l'expérience client digitale » (1 :
  `DIG-CLI-001`), « Moderniser le système d'information » (1 : `RH-PORT-002`),
  « Accélérer la transformation cloud » (1 : `CLOUDMIG-004`) — somme = total (5).
- **Workflow des demandes** validé de bout en bout via l'API : création
  « Brouillon » → soumission → évaluation (responsable) → acceptation →
  transformation en initiative `DEM-xxxxxx` (À planifier, budget 0, avancement
  0) ; refus avec et sans motif ; transitions interdites → 400 ; seconde
  transformation → 400 ; `RESP-204122` jamais supprimée.
- **404 JSON** confirmé sur route inconnue avec jeton.
- **Répartitions** `parEntite` / `parResponsable` / `parObjectif` confirmées.

---

## 10. Conclusion, limites et perspectives

### Bilan

Le projet a été consolidé sans rupture : la stack et les fonctionnalités sont
conservées, les incohérences CDC ↔ application sont corrigées, les KPI sont
désormais calculés depuis la base, la sécurité est renforcée, et la qualité est
adossée à des tests automatisés (39 unitaires + 102 fonctionnels) et des builds
vérifiés. Le périmètre a été étendu aux priorités P1 (**Kanban**, **Roadmap**)
et P2 (**objectifs stratégiques**, **demandes d'initiatives** au cycle contrôlé),
toutes vérifiées par des scénarios API dédiés et des tests unitaires.

### Limites

- Les tests unitaires s'appuient sur des **mocks** ; ils ne remplacent pas des
  tests d'intégration sur base réelle (possibles via Testcontainers).
- La suite API est écrite en PowerShell (Windows) ; une version portable
  (Node/Python) serait un plus.
- Le glisser-déposer du **Kanban** appelle l'API de changement de statut : un
  déplacement interdit par le cycle de vie est refusé (400) et le message
  d'erreur est affiché au-dessus du tableau.
- Les 10 croquis UML fournis n'étaient pas exploitables par l'outil ; les
  diagrammes ont été régénérés à partir du code et du cahier des charges, à
  faire valider visuellement par l'encadrant.

### Perspectives

- CI/CD : **workflow GitHub Actions fourni** (`.github/workflows/ci.yml` —
  `mvn test` + `npm run build`), à activer sur le dépôt distant ; extension
  possible avec les tests API sous MySQL.
- Conteneurisation Docker (app + MySQL) et configuration par variables
  d'environnement.
- Exécution des tests d'intégration via Testcontainers MySQL.
- Journalisation d'audit et export CSV/PDF des initiatives.
