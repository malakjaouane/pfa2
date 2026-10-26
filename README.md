# Portefeuille d'initiatives digitales — IT6 Consulting

Application web de gestion d'un portefeuille d'initiatives digitales : suivi des
initiatives, de leurs étapes et jalons, calcul de KPI (dashboard) et administration
des utilisateurs, rôles et permissions.

Ce dépôt est la version consolidée et professionnalisée du projet PFA. La démarche
appliquée suit le fil conducteur **Sujet → Cahier des charges → UML → Architecture
→ Base de données → API → Frontend**.

---

## Sommaire

1. [Stack technique](#stack-technique)
2. [Architecture du dépôt](#architecture-du-dépôt)
3. [Prérequis](#prérequis)
4. [Installation et lancement](#installation-et-lancement)
5. [Comptes de démonstration](#comptes-de-démonstration)
6. [Configuration (.env)](#configuration-env)
7. [Fonctionnalités](#fonctionnalités)
8. [Règles métier](#règles-métier)
9. [Tests](#tests)
10. [Documentation UML](#documentation-uml)
11. [Charte graphique](#charte-graphique)
12. [Limites connues](#limites-connues)

---

## Stack technique

| Couche | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 3.5.16, Spring Data JPA (Hibernate), Spring Security, JWT (jjwt), Bean Validation, Maven |
| Base de données | MySQL 8.4 (schéma SQL versionné) |
| Frontend | React 18, Vite 5, React Router 6, Axios, Chart.js (react-chartjs-2) |
| Sécurité | Mots de passe BCrypt, jetons JWT (HS256), autorisations par permission |

---

## Architecture du dépôt

```
Portefeuille-d-initiatives-digitales/
├── backend/                     # API REST Spring Boot
│   ├── src/main/java/ma/it6/portefeuille/
│   │   ├── config/              # Sécurité, initialisation des données
│   │   ├── controller/          # Endpoints REST
│   │   ├── dto/                 # Contrats d'entrée/sortie
│   │   ├── entity/              # Entités JPA (MPD)
│   │   ├── exception/           # Gestion centralisée des erreurs (400/401/403/404/409/500)
│   │   ├── mapper/              # Conversion entité ↔ DTO
│   │   ├── repository/          # Accès aux données + requêtes KPI
│   │   ├── security/            # Filtre JWT, service JWT, principal
│   │   ├── service/             # Règles métier (InitiativeService, DashboardService…)
│   │   └── specification/       # Filtres dynamiques (recherche, statut…)
│   └── src/test/java/…          # Tests unitaires JUnit 5 + Mockito
├── frontend/                    # Application React/Vite
│   └── src/                     # pages, composants, services API, styles
├── database/                    # SOURCE UNIQUE SQL + scripts d'exploitation
│   ├── schema.sql               # MPD MySQL 8 (structure)
│   ├── data.sql                 # Référentiels (rôles, permissions, entités, statuts…)
│   ├── init-mysql.ps1           # Initialisation du datadir MySQL local
│   ├── start-mysql.ps1          # Démarrage du serveur MySQL
│   └── tests-api.ps1            # Suite de tests fonctionnels de l'API
├── docs/UML/                     # Diagrammes (images de référence + Mermaid/PlantUML)
└── .env.example                 # Modèle de variables d'environnement
```

> Le schéma et les données de référence (`database/*.sql`) sont **embarqués dans le
> jar** par le bloc `<resources>` de `pom.xml` (sous `db/`) puis exécutés au
> démarrage via `spring.sql.init`. Il n'y a donc qu'une seule source de vérité SQL.

---

## Prérequis

- **JDK 17+**
- **Maven 3.9+** (ou le Maven local fourni dans `tools/`)
- **Node.js 18+** et npm
- **MySQL 8.4** (installation locale ; scripts fournis dans `database/`)

---

## Installation et lancement

### 1. Base de données MySQL

```powershell
# (une seule fois) initialiser le datadir local + créer base, utilisateur et schéma
powershell -NoProfile -ExecutionPolicy Bypass -File database\init-mysql.ps1

# démarrer le serveur MySQL local (port 3306)
powershell -NoProfile -ExecutionPolicy Bypass -File database\start-mysql.ps1
```

Le schéma et les référentiels sont ensuite appliqués automatiquement au démarrage
du backend (idempotent).

### 2. Backend (API REST)

```powershell
cd backend
& .\..\tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
# ou, après packaging :
& .\..\tools\apache-maven-3.9.16\bin\mvn.cmd clean package
java -jar target\portefeuille-backend-0.0.1-SNAPSHOT.jar
```

L'API écoute sur `http://localhost:8080/api`.

> Si Maven est déjà installé sur la machine, les commandes `mvn spring-boot:run`
> et `mvn clean package` fonctionnent à l'identique ; le dossier `tools/` n'est
> qu'une copie locale de Maven.

### 3. Frontend (React)

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

L'application est disponible sur `http://localhost:5173`. Le serveur de
développement redirige `/api` vers le backend (`vite.config.js`).

> Sous Windows PowerShell 5.1, utilisez `npm.cmd` (et non `npm`) si la politique
> d'exécution bloque les scripts `.ps1`.

---

## Comptes de démonstration

Créés automatiquement au premier lancement (mots de passe hashés BCrypt) :

| Profil | Email | Mot de passe | Permissions |
|---|---|---|---|
| Administrateur | `admin@it6.ma` | `Admin@123` | 16 (toutes) |
| Responsable | `responsable@it6.ma` | `Resp@123` | 13 |
| Visiteur | `visiteur@it6.ma` | `Visi@123` | 3 (consultation) |

> Ces comptes et les initiatives de démonstration ne sont créés que si les tables
> sont vides : aucune donnée existante n'est écrasée.

---

## Configuration (.env)

Le backend lit ses paramètres dans `application.yml`, tous surchargeables par
variables d'environnement. Copiez `.env.example` en `.env` et adaptez au besoin.

| Variable | Rôle | Défaut (dev) |
|---|---|---|
| `DB_URL` | URL JDBC MySQL | `jdbc:mysql://127.0.0.1:3306/portefeuille_initiatives…` |
| `DB_USERNAME` | Utilisateur MySQL | `pfa_user` |
| `DB_PASSWORD` | Mot de passe MySQL | `pfa2026` (exemple de développement, à changer) |
| `JWT_SECRET` | Clé de signature HS256 (≥ 32 octets) | clé de développement |
| `JWT_EXPIRATION_MS` | Durée de validité du jeton | `28800000` (8 h) |
| `SERVER_PORT` | Port HTTP du backend | `8080` |
| `ANTHROPIC_API_KEY` | Clé API Anthropic de l'assistant IA — **absente** : la page `/assistant` affiche « indisponible » (503 explicite, jamais de panne) | *vide* |
| `IA_MODELE` / `ANTHROPIC_MODELE` | Modèle utilisé par l'assistant | `claude-haiku-4-5` |
| `IA_FORMAT` | Format d'appel : `anthropic` (API Anthropic) ou `openai` compatible OpenAI — **Ollama local sans aucune clé** (`openai` + `IA_BASE_URL=http://host.docker.internal:11434`) | `anthropic` |
| `IA_ACTIF` | Kill switch global de l'assistant (`false` = désactivé même avec une clé) | `true` |

**En production, remplacez impérativement `DB_PASSWORD` et `JWT_SECRET`.**
La clé `ANTHROPIC_API_KEY` reste exclusive à `.env` (ignoré par git) : elle
n'est ni versionnée ni journalisée côté serveur.

---

## Fonctionnalités

- **Authentification** JWT (login/logout) et gestion des droits par permission.
- **Dashboard / KPI** calculés par l'API : total, en cours, terminées, en pause,
  en retard, budget total, avancement moyen ; répartitions par statut, priorité,
  entité, responsable et **objectif stratégique** ; liste des initiatives en
  retard.
- **Budgets** : le montant affiché est le `budget` **saisi**. L'application
  n'enregistre **aucune dépense réelle** (ni saisie de factures, ni table de
  mouvements). Les agrégats dérivés exposés par `GET /api/budget`
  (`consomme`, `effectif`, `ecart`) sont des **estimations calculées au
  prorata du taux d'avancement** (`budget × avancement / 100`) : un
  indicateur de pilotage, pas une comptabilité. La précision est portée
  explicitement à l'écran (légende du tableau de bord, colonne « Budget »,
  fiche d'initiative) — voir `docs/API.md` §3.
- **Initiatives** : création, consultation, modification, archivage ; cycle de vie
  des statuts contrôlé.
- **Étapes et jalons** : suivi de l'avancement, dates, statuts dédiés, gestion de
  la date de réalisation des jalons.
- **Recherche et filtres** combinables (texte, statut, priorité, entité,
  responsable, objectif, archivage).
- **Kanban** : bascule Tableau / Kanban sur la page Initiatives (5 colonnes par
  statut, cartes cliquables, retard en évidence, changement de statut par
  glisser-déposer).
- **Roadmap** (`/roadmap`) : timeline mensuelle calculée côté frontend depuis les
  dates réelles, barres colorées par statut, ligne « aujourd'hui », initiatives
  non planifiées listées à part.
- **Objectifs stratégiques** (`/objectifs`) : référentiel, rattachement des
  initiatives (formulaire + fiche + filtre + graphique dashboard).
- **Demandes d'initiatives** (`/demandes`) : workflow à états contrôlés
  `Brouillon → Soumise → En évaluation → Acceptée/Refusée`, refus avec motif
  facultatif, transformation en initiative (code `DEM-xxxxxx`, aucune création
  en double).
- **Historique des statuts** (`/historique`) : journal global de tous les
  changements de statut — recherche par code ou nom
  d'initiative (insensible à la casse et aux accents), filtres statut,
  utilisateur et période, tri (récent / ancien), pagination et export CSV de
  l'affichage. Chaque ligne porte son **origine** (manuelle ou automatique,
  colonne `historique_statut.automatique` alimentée par le moteur de règles).
  Les événements sont écrits en base dans la même transaction que le
  changement (aucune donnée fictive) ; la fiche initiative n'en garde qu'un
  résumé (5 derniers changements) plus un bouton « Voir tout l'historique »
  qui ouvre la page déjà filtrée. Lecture soumise à `INITIATIVE_CONSULTER`
  (aucun nouveau droit) ; les **administrateurs** (`UTILISATEUR_GERER`,
  droit existant) peuvent supprimer une entrée erronée via la colonne
  « Actions » (confirmation systématique, `DELETE` refusée aux autres
  profils) — aucune autre écriture n'est exposée.
- **Historique des connexions** (`/historique-connexions`) : journal de
  contrôle d'accès (loi 09-08) alimenté à **chaque tentative de
  connexion**, réussie ou échouée (mauvais mot de passe, compte désactivé,
  verrouillage 429) avec horodatage, e-mail saisi, adresse IP et motif.
  Recherche (e-mail, IP ou nom), filtre de résultat (réussies /
  échouées), période du/au, pagination — puis **suppression d'une ligne
  ou purge totale** avec confirmation. Page réservée aux administrateurs
  (`UTILISATEUR_GERER` sur toute la route, aucun nouveau droit) ; l'e-mail
  est conservé même quand le compte a été supprimé (clé étrangère
  `ON DELETE SET NULL`), et l'écriture est en transaction propre : une
  panne du journal ne fait jamais échouer une connexion valide.
- **Assistant IA** (« Demande à ton portefeuille », permission dédiée
  `IA_UTILISER` — 17ᵉ, ADMIN + RESPONSABLE) : chat et résumé d'initiative
  (`/assistant`, bouton « Résumé IA » sur la fiche) branchés sur l'API
  Anthropic. Le modèle reçoit à chaque requête un **contexte reconstruit
  depuis la base** (initiatives non archivées, statut, avancement, santé,
  budget, responsable) et le prompt système lui interdit d'inventer —
  réponses sourcées, jamais hallucinées (loi 09-08 / CNDP). **Kill switch**
  `IA_ACTIF=false` (désactivation immédiate sans redéploiement), clé absente
  → état « indisponible » explicite (503 + message, jamais d'erreur muette),
  et **journalisation intégrale** de chaque requête (`journal_ia` : auteur,
  demande, réponse, statut OK / ERREUR / NON_DISPONIBLE, durée — consultable
  par les administrateurs).
- **Administration** : utilisateurs, rôles, permissions. Les comptes se
  **suppriment définitivement** depuis la colonne « Actions » (bouton
  masqué sur son propre compte, confirmation systématique) ; le serveur
  refuse en 400 son propre compte, le dernier administrateur actif et un
  compte qui porte encore des demandes (désactivation conseillée à la
  place).

---

## Règles métier

Les règles RM01 → RM16 du cahier des charges sont vérifiées côté backend
(service + validation) et couvertes par les tests. Points notables :

- **RM07** : une initiative « Terminée » doit être à 100 % ; inversement, un
  avancement de 100 % la fait passer automatiquement à « Terminée ».
- **Cycle de vie** : `À planifier → En cours → En pause ↔ En cours → Terminée`,
  et `À planifier / En cours / En pause → Annulée`. L'**archivage n'est pas un
  statut** : seules les initiatives « Terminée » ou « Annulée » sont archivables.
- **Création** : le statut est **dérivé par le domaine** (« À planifier », ou
  « Terminée » si créée à 100 %) ; la valeur de statut envoyée par le client est
  ignorée, ce qui évite les incohérences avec le formulaire.

---

## Tests

### Tests unitaires backend (JUnit 5 + Mockito)

```powershell
cd backend
& .\..\tools\apache-maven-3.9.16\bin\mvn.cmd test
```

Couvrent `InitiativeService` (création, transitions, RM04/05/06/07/14),
`InitiativeMapper` (règle « en retard »), `DashboardService` (agrégation des KPI),
`CalculeurSante` (score de santé automatique : archivée / terminée / date
butor / jalons en retard / écart planning-avancement, et chargement en lot des
jalons sans N+1),
`BudgetService` (consommé / effectif / écart), `DemandeService`
(workflow des demandes, transformation en initiative), `EtapeService`
(étapes et jalons), `HistoriqueStatutService` (origine manuelle/automatique des
lignes du journal, filtres/tri/pagination de la lecture globale, suppression
d'une entrée avec 404 si elle n'existe pas, purge totale en un seul
`DELETE`), `DemandeService` (recherche `%…%` sur titre/besoin/justification
et suppression refusée à 400 si la demande est déjà transformée),
`InitiativeService` (purge définitive réservée aux initiatives archivées),
`SecuriteApiTest` (règles d'URL et filtrage JWT, dont lecture et suppression du
journal, purge totale du journal, suppression des demandes `DEMANDE_EVALUER`,
purge des initiatives `INITIATIVE_ARCHIVER`, **suppression d'utilisateur
`UTILISATEUR_GERER`** et **accès admin unique à l'historique des connexions**,
tous verbes) et
`ApiErreurHttpTest` (codes HTTP d'erreur : 405 / 400 / 415 / 406 au lieu de 500)
et `DashboardServiceTest` (dont l'accueil personnalisé : jalons de la semaine
et initiatives à risque) et `UtilisateurServiceTest` (suppression définitive
d'un compte : propre compte, dernier admin actif, compte porteur de demandes —
trois refus 400) et `HistoriqueConnexionServiceTest` (écriture de chaque
tentative de connexion, normalisation e-mail, filtres/tri/pagination, 404 et
purge). **169 tests, 0 échec.**

### Tests fonctionnels de l'API

```powershell
# le backend doit être démarré (local : valeur par défaut)
powershell -NoProfile -ExecutionPolicy Bypass -File database\tests-api.ps1
# backend Docker : -Base http://127.0.0.1:8081/api
```

Suite idempotente (codes/emails uniques par exécution) qui couvre
l'authentification, les KPI, les filtres, les règles métier, le cycle de vie,
les autorisations, les utilisateurs, les rôles, les **objectifs stratégiques**,
le **workflow des demandes**, l'**historique des statuts** (sections 10, 11 et
12 : page globale, origine manuelle/automatique, recherche, filtres statut /
utilisateur / période, pagination, tri, permissions, création interdite et
suppression administrative 401/403/404/204) et le **score de santé** (niveau et
motif sur chaque ligne de la liste, de la fiche et de la surveillance du
dashboard, rouge sur une initiative en retard, absent des écritures). Les KPI
sont comparés à un recalcul indépendant issu de la liste réelle des initiatives
(aucune valeur codée en dur) ; deux contrôles vérifient enfin les KPI d'accueil
`jalonsSemaine` (fenêtre du jour au dimanche courant) et `initiativesARisque`
(santé ORANGE ou ROUGE recalculée depuis la liste). La section 13 couvre
l'**assistant IA** : `IA_UTILISER` exigée (403 visiteur), statut de
disponibilité, question réelle (200 + réponse, ou 503 explicite si non
configuré), résumé d'inconnu → 404, journal accessible aux seuls
administrateurs. Les sections 11 à 13 couvrent en outre la **recherche et la
suppression des demandes** (401 anonyme, 403 visiteur, 404 après suppression,
400 si déjà transformée), la **purge des initiatives archivées** (400 si non
archivée, 204 puis 404 après purge) et le refus de la **purge du journal**
hors administrateur (401 / 403). Les sections 14 et 15 couvrent la
**suppression d'utilisateur** (401 anonyme, 403 visiteur et responsable, 404
inconnu, 400 sur son propre compte, parcours nominal 201 → 204 → 404, 400 avec
message quand le compte porte des demandes) et l'**historique des connexions**
(admin seuls en lecture : 401 / 403, 200 + `X-Total-Count`, recherche par
e-mail, filtre « échouées » après une tentative réelle, période inversée →
400, POST interdit, suppression de ligne 401/403/404/204, purge refusée hors
administrateur). **204 scénarios, 0 échec.**

Après une campagne de tests, purgez les résidus (initiatives `TEST*` archivées,
initiatives issues de demandes de test `DEM-*`, demandes `TEST-DEM*`, objectifs
`TEST-OBJ*` et comptes `test*@it6.ma`) avec le script fourni :

```powershell
Get-Content database\cleanup-test-data.sql | mysql -u pfa_user -p portefeuille_initiatives
```

---

## Limites connues

Points connus et acceptés pour un prototype, à traiter avant une mise en production :

- **Jeton JWT dans `localStorage`** : exposé en cas de faille XSS. Évolution
  possible : cookie `HttpOnly`.
- **Anti force brute en mémoire** (`ProtectionConnexions`) : compteurs perdus au
  redémarrage et non partagés entre plusieurs instances du backend.
- **Comptes de démonstration** (`admin@it6.ma`, `responsable@it6.ma`,
  `visiteur@it6.ma`) aux mots de passe connus : à modifier ou supprimer en production.
- **Secrets de développement** (`DB_PASSWORD`, `JWT_SECRET`) : valeurs par défaut
  tolérées uniquement en profil `dev` / `test` / `local` ; le démarrage est refusé
  sinon.
- **Droits par rôle, non par initiative** : un responsable disposant de
  `INITIATIVE_MODIFIER` peut modifier toute initiative non archivée.
- **Objectifs stratégiques** : pas de permission dédiée ; l'écriture exige
  `INITIATIVE_MODIFIER`.

---

## Documentation UML

Les 11 diagrammes de référence (cas d'utilisation, classes, BPMN, MCD, MLD, MPD
et cinq séquences) sont fournis en images dans `docs/UML/images/` ; la liste
détaillée est dans `docs/UML/README.md`.

Les sources `diagrammes-mermaid.md` et `plantuml/*.puml` complètent ce jeu
(états, activité, composants, déploiement, KPI).

## Documentation complémentaire

| Document | Contenu |
|---|---|
| `docs/TRACABILITE-CDC.md` | Matrice exigences du cahier des charges (RM01→RM16, KPI, filtres, permissions, P1/P2) → code → tests |
| `docs/API.md` | Référence complète de l'API REST (routes, permissions requises, formats) |
| `docs/RAPPORT-FINAL.md` | Rapport final structuré en 10 sections |
| `docs/CAHIER_DES_CHARGES_…CORRIGEE.docx` | Cahier des charges (version professionnelle corrigée) |
| `.github/workflows/ci.yml` | Intégration continue (tests backend + build frontend) |

---

## Charte graphique

Toute l'interface — **application et vitrine** — applique la **charte
ServiceNow** (« brand evolution »), via les variables CSS de
`frontend/src/styles.css` :

| Rôle | Usage | Code |
|---|---|---|
| Navy | barre supérieure, hero, bandeaux | `#032D42` |
| Vert wasabi | boutons primaires, accents, item actif | `#62D84E` |
| Vert foncé | textes accentués sur fond clair | `#298319` |
| Cyan | statut « En cours », graphiques, halos | `#24C2CE` |
| Teal | liens (texte) | `#00718F` |
| Violet | accent secondaire | `#5274FF` |
| Fond | arrière-plan des pages | `#F5F6F7` |
| Texte | titres et contenu | `#1D1D1D` |

Concrètement :

- **Barre supérieure navy** (comme le « Next Experience » de ServiceNow), avec
  recherche et profil en surimpression claire.
- **Barre latérale claire** : item actif en pastille **vert clair** (`#E0F7DC`)
  à texte navy, souligné d'un **liseré vert**.
- **Boutons primaires verts à texte navy** — la signature de ServiceNow.
- Les **statuts métier** restent distincts et lisibles : *À planifier* gris,
  *En cours* bleu cyan (`#D6EEF3` / `#0E7180`), *En pause* orange, *Terminée*
  vert, *Annulée* rouge.

Typographie : **Poppins** pour les titres (équivalent libre de la police *Gilroy*
de ServiceNow) et **Inter** pour le corps. Les icônes proviennent d'un jeu **SVG
maison** (`frontend/src/components/Icons.jsx`) — plus aucun emoji.

La palette initialement imposée (`#A0D2EB`, `#E5EAF5`, `#D0BDF4`, `#8458B3`,
`#A28089`) reste documentée dans l'historique ; elle a été remplacée à la demande
du commanditaire, d'abord par une identité type Jira, puis par la charte
ServiceNow appliquée à l'ensemble du produit.

### Vitrine publique — inspiration ServiceNow

La page **publique** (avant connexion) est la source de cette direction
artistique. Elle reprend la **structure et les codes visuels** du site
ServiceNow : nav collante, hero navy à halos, cartes produit, bandeau d'appel à
l'action et pied de page à plusieurs colonnes. Palette extraite des variables
officielles du design system ServiceNow :

| Rôle | Usage | Code |
|---|---|---|
| Navy | fond du hero et des bandeaux | `#032D42` |
| Vert wasabi | boutons primaires, accents | `#62D84E` |
| Cyan | dégradés, halo du hero | `#24C2CE` |
| Violet | accent secondaire | `#5274FF` |
| Texte | titres et contenu | `#1D1D1D` |

Les boutons sont des **pilules vertes** à texte foncé — la signature de ServiceNow.

## Page d'accueil publique

Une page vitrine (`frontend/src/pages/LandingPage.jsx`, route `/`) est présentée
avant la connexion, dans la direction artistique ServiceNow décrite ci-dessus.
Elle est volontairement riche, à l'image d'un site produit SaaS :

- **Bandeau d'annonce** navy puis **barre de navigation** blanche et collante
  (logo vert, liens d'ancrage, bouton vert « Se connecter »).
- **Hero navy** (`#032D42`) avec halos cyan/vert, titre dont un mot est accentué
  en vert, double appel à l'action, liste d'avantages et **maquette du tableau
  de bord**.
- **Bandeau défilant** façon sponsors : deux rangées qui défilent en continu
  (technologies, puis modules), avec estompage sur les bords et pause au survol.
- **Bandeau vidéo** en lecture automatique, muette et en boucle (« Le pilotage,
  en temps réel »).
- **Six modules** en cartes (tableaux de bord, kanban, roadmap, demandes,
  objectifs, sécurité).
- **Trois sections fonctionnalité** en alternance texte / maquette : *Kanban*,
  *Roadmap* et *Demandes*.
- **Section « Notre approche »** : texte + **photo libre** (Unsplash).
- **Section « Cas d'usage »** : trois cartes illustrées par des **photos libres**
  (Unsplash).
- **Profils** (administrateur, responsable, visiteur), **valeurs** (4 cartes),
  **bandeau d'appel à l'action** et **pied de page** à plusieurs colonnes.

Les **photos** proviennent d'**Unsplash** et la **vidéo** de **Pexels**
(utilisations libres) ; elles sont chargées depuis leur CDN et nécessitent donc
une connexion Internet. Les maquettes d'interface, elles, sont **décoratives**
(rendues en CSS) et n'affichent aucun chiffre réel, conformément au principe
« aucune donnée simulée ». Si une session est active, le bouton principal mène
directement au `/dashboard`.

## Tableau de bord (page d'accueil applicative)

La page `/dashboard` (`frontend/src/pages/DashboardPage.jsx`) a été conçue comme
un tableau de bord de portefeuille inspiré des solutions SaaS (Planview,
ServiceNow SPM, Jira Align, Microsoft Planner). Toutes les valeurs proviennent de
l'API réelle — aucun chiffre n'est simulé :

- **En-tête** : message d'accueil nominatif, phrase de présentation et bouton
  **« Nouvelle initiative »** affiché uniquement avec la permission `INITIATIVE_CREER`.
- **Cartes KPI** : initiatives actives, en cours, terminées, en retard et budget
  total (format compact `k MAD` / `M MAD`).
- **État du portefeuille** : anneau de répartition par statut (couleurs alignées
  sur les badges), barres par priorité et indicateur d'avancement moyen.
- **Initiatives récentes** : tableau (code, initiative, responsable, statut,
  priorité, avancement, échéance) issu de `GET /api/initiatives`, avec recherche
  locale et lien « Voir toutes les initiatives ». La ligne mène au détail.
- **À surveiller** : initiatives en retard (`initiativesEnRetard`) avec échéance,
  nombre de jours de retard et avancement.
- États de chargement (squelettes), d'erreur et vides gérés ; mise en page
  responsive.

Le gabarit (`frontend/src/components/Layout.jsx`) comporte désormais une **barre
supérieure** (fil d'Ariane calculé depuis la route, recherche globale qui filtre
réellement les initiatives via `/initiatives?recherche=…`, menu profil avec
déconnexion) et une **barre latérale** avec le lien « Archives »
(`/initiatives?archive=1`). Sur mobile, la barre latérale devient un tiroir
coulissant.
