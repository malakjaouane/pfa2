# Référence de l'API REST

**Base URL :** `http://localhost:8080/api`
**Base URL (frontend dev) :** via proxy Vite `http://localhost:5173/api`

Toutes les routes (sauf login) exigent un en-tête :

```
Authorization: Bearer <jeton JWT>
```

---

## 1. Formats communs

### Erreurs

Toute erreur renvoie un corps JSON :

```json
{
  "timestamp": "2026-10-09T…",
  "status": 404,
  "error": "Not Found",
  "message": "Ressource introuvable : /api/inconnue",
  "details": []
}
```

| Code | Cas |
|---|---|
| 400 | Règle métier violée (RM01/04/05/06/07…), validation Bean Validation, transition interdite, code dupliqué |
| 401 | Absence de jeton, jeton invalide/expiré, identifiants incorrects |
| 403 | Permissions insuffisantes |
| 404 | Initiative/utilisateur/… inexistant, route inconnue (réponse **JSON**, plus de page HTML) |
| 409 | Contrainte d'intégrité (valeur dupliquée en base) ou modification concurrente (verrouillage optimiste, RC06) |
| 429 | Trop de tentatives de connexion (limitation par IP et e-mail) |
| 500 | Erreur interne |

---

## 2. Authentification

| Méthode | Route | Permission | Description |
|---|---|---|---|
| POST | `/auth/login` | publique | Connexion → `{ token, type, idUser, nom, prenom, email, role, entite, permissions[] }` |
| GET | `/auth/me` | connecté | Profil + permissions du compte courant |
| POST | `/auth/changer-mot-de-passe` | connecté | Changement du mot de passe du compte courant |

```json
{ "email": "admin@it6.ma", "motDePasse": "Admin@123" }
```

---

## 3. Dashboard / KPI

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/dashboard` | `DASHBOARD_CONSULTER` | `totalInitiatives, enCours, terminees, enPause, enRetard, jalonsEnRetard, jalonsSemaine, initiativesARisque, budgetTotal, avancementMoyen, parStatut[], parPriorite[], parEntite[], parResponsable[], parObjectif[], initiativesEnRetard[], risquesCritiques[]` |

### Budgets (`GET /budget`, `/budget/entites`, `/budget/objectifs`)

Permission `BUDGET_CONSULTER`. Les trois routes acceptent **les mêmes filtres**
que `GET /initiatives` : le budget affiché correspond donc toujours à la
sélection visible à l'écran.

> **Ces montants sont des PROJECTIONS, pas une comptabilité.** Le cahier des
> charges ne prévoit **aucune saisie de dépenses réelles** : il n'existe ni table
> de mouvements financiers, ni saisie de factures. Le champ `budget` d'une
> initiative est le montant **saisi** ; tout montant dérivé en dehors de lui est
> une **estimation**.

| Champ | Définition |
|---|---|
| `budgetTotal` | Somme des `budget` saisis — le montant engagé, pas une dépense |
| `consomme` | **Estimation** `budget × avancement / 100` : la part du budget que l'avancement laisse supposer consommée, **calculée au prorata du taux d'avancement**. Ce n'est pas un cumul de dépenses réelles |
| `budgetRestant` | `budgetTotal − consomme` — recoupement exact : `budgetTotal = consomme + budgetRestant` |
| `effectif` | Budget des seules initiatives closes (« Terminée » ou « Annulée ») |
| `ecart` | `effectif − consomme` — négatif quand le portefeuille « consomme au prorata » davantage que ce qui est définitivement clos |
| `ecartPourcentage` | `ecart / consomme × 100`, `null` quand `consomme` vaut 0 (pas de division par zéro) |
| `parStatut[]`, `parEntite[]`, `parObjectif[]`, `parPriorite[]` | Les mêmes agrégats (`budget`, `consomme`, `effectif`, `ecart`) ventilés par dimension |

Côté interface, la même précision est portée explicitement : la légende sous les
indicateurs du tableau de bord, l'infobulle de la colonne « Budget » de la liste
et la note sous le budget de la fiche d'une initiative.

---

## 4. Initiatives

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/initiatives` | `INITIATIVE_CONSULTER` | Liste + filtres combinés (tous optionnels) |
| GET | `/initiatives/{id}` | `INITIATIVE_CONSULTER` | Détail d'une initiative |
| POST | `/initiatives` | `INITIATIVE_CREER` | Création (statut dérivé par le domaine : « À planifier », ou « Terminée » si avancement 100 %) |
| PUT | `/initiatives/{id}` | `INITIATIVE_MODIFIER` | Modification + contrôle du cycle de vie |
| PUT | `/initiatives/{id}/statut` | `INITIATIVE_MODIFIER` | Changement de statut seul (Kanban, glisser-déposer) : `{ idStatut, commentaire? }`, mêmes règles de cycle de vie que la modification |
| GET | `/initiatives/{id}/historique` | `INITIATIVE_CONSULTER` | Journal des modifications de la fiche (paginé, `X-Total-Count`, filtre `type` ∈ `initiative`/`etape`/`jalon`) |
| PATCH | `/initiatives/{id}/archive` | `INITIATIVE_ARCHIVER` | Archivage (réservé à « Terminée » / « Annulée », RC03) |
| DELETE | `/initiatives/{id}` | `INITIATIVE_ARCHIVER` | Purge définitive d'une initiative **archivée** uniquement (**400** sinon) : étapes, jalons et lignes d'historique partent en `ON DELETE CASCADE`, la demande liée et le journal IA basculent en `NULL` |

### Filtres (`GET /initiatives`)

| Paramètre | Type | Exemple |
|---|---|---|
| `statut` | `long` | `statut=2` |
| `entite` | `long` | `entite=1` |
| `responsable` | `long` | `responsable=2` |
| `priorite` | `long` | `priorite=3` |
| `dateDu` / `dateAu` | `date` | `dateDu=2026-01-01&dateAu=2026-12-31` — initiatives actives sur la période (dont la durée chevauche l'intervalle) |
| `recherche` | `string` | `recherche=MOB` |
| `objectif` | `long` | `objectif=1` |
| `archive` | `boolean` | `archive=true` |
| `page`, `size` | `int` | pagination facultative (`page` à partir de 0) ; sans elle, liste complète |
| `tri`, `direction` | `string` | tri facultatif sur un champ autorisé (`asc` / `desc`) |

L'en-tête de réponse `X-Total-Count` donne le nombre total de résultats hors pagination. `GET /dashboard` accepte les mêmes filtres.

### Corps de création / modification (`InitiativeRequest`)

```json
{
  "code": "NEW-APP-005",
  "nom": "Nouvelle application",
  "description": "…",
  "dateDebut": "2026-10-01",
  "dateFinPrevue": "2027-03-31",
  "budget": 750000.00,
  "avancement": 10,
  "idEntite": 1,
  "idResponsable": 2,
  "idStatut": 2,
  "idPriorite": 3,
  "idObjectif": 1
}
```

`idStatut` est facultatif : à la création le statut est dérivé par le système (RC01, la valeur envoyée est ignorée) ; à la modification, son absence conserve le statut actuel.

### Objet liaison (`initiative.objectif`)

Les initiatives exposent leur rattachement à un objectif stratégique :

```json
{ "idObjectif": 1, "code": "OBJ-EXP", "libelle": "Optimiser l'expérience client", "description": "…" }
```

---

## 5. Étapes

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/initiatives/{idInitiative}/etapes` | `INITIATIVE_CONSULTER` | Étapes d'une initiative (tri par ordre) |
| POST | `/initiatives/{idInitiative}/etapes` | `ETAPE_GERER` | Ajout (ordre auto = max+1, statut par défaut « À faire ») |
| PUT | `/etapes/{idEtape}` | `ETAPE_GERER` | Modification ; recalcule l'avancement de l'initiative |
| DELETE | `/etapes/{idEtape}` | `ETAPE_GERER` | Suppression (refusée si l'initiative est archivée) ; l'ordre est recompacté |
| PUT | `/initiatives/{idInitiative}/etapes/ordre` | `ETAPE_GERER` | Réordonner les étapes |

---

## 6. Jalons

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/initiatives/{idInitiative}/jalons` | `INITIATIVE_CONSULTER` | Jalons d'une initiative |
| POST | `/initiatives/{idInitiative}/jalons` | `JALON_GERER` | Ajout (statut par défaut « À atteindre ») |
| PUT | `/jalons/{idJalon}` | `JALON_GERER` | Modification (RM15 : date de réalisation gérée selon le statut) |
| DELETE | `/jalons/{idJalon}` | `JALON_GERER` | Suppression |

---

## 7. Référentiels

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/referentiels/statuts-initiative` | connecté | Statuts métier (À planifier, En cours, En pause, Terminée, Annulée) |
| GET | `/referentiels/priorites` | connecté | Priorités |
| GET | `/referentiels/statuts-etape` | connecté | Statuts d'étape |
| GET | `/referentiels/statuts-jalon` | connecté | Statuts de jalon |
| GET | `/referentiels/entites` | connecté | Entités actives |
| GET | `/referentiels/responsables` | connecté | Responsables potentiels (utilisateurs actifs) |
| GET | `/referentiels/statuts-demande` | connecté | Statuts de demande (Brouillon, Soumise, En évaluation, Acceptée, Refusée) |
| GET | `/permissions` | connecté | Liste des permissions |

---

## 8. Utilisateurs (administrateur)

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/users` | `UTILISATEUR_GERER` | Liste des utilisateurs |
| GET | `/users/{id}` | `UTILISATEUR_GERER` | Détail |
| POST | `/users` | `UTILISATEUR_GERER` | Création (mot de passe hashé BCrypt) |
| PUT | `/users/{id}` | `UTILISATEUR_GERER` | Modification (dont activation/désactivation) |
| DELETE | `/users/{id}` | `UTILISATEUR_GERER` | **Suppression définitive** d'un compte, **204** si supprimé, **404** si absent |

Trois garde-fous renvoient **400** avec un message explicite :

1. **son propre compte** — on ne se supprime pas soi-même ;
2. **le dernier administrateur actif** — continuité d'exploitation
   (même garde-fou que la modification) ;
3. **un compte qui porte encore des demandes** — la clé étrangère
   `demande_initiative.demandeur` est sans cascade : on conseille la
   désactivation (les demandes restent attachées au compte).

Les autres liens (initiatives en charge, historique des modifications,
connexions) sont en `ON DELETE SET NULL` : le compte part, ses œuvres
restent — et ses lignes de journal gardent leur auteur anonymisé en `NULL`. La session en cache du compte supprimé est invalidée pour couper
son accès à la requête suivante.

---

## 9. Rôles et permissions

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/roles` | connecté | Rôles (ADMIN, RESPONSABLE, VISITEUR) avec nombre de permissions |
| GET | `/roles/{id}` | connecté | Détail + permissions |
| PUT | `/roles/{id}/permissions` | `ROLE_GERER` | Mise à jour des permissions d'un rôle (`{ "idsPermissions": [1, 9, 10] }`) |

---

## 10. Objectifs stratégiques

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/objectifs` | connecté | Référentiel des objectifs stratégiques |
| POST | `/objectifs` | `INITIATIVE_MODIFIER` | Création (code unique) |
| PUT | `/objectifs/{id}` | `INITIATIVE_MODIFIER` | Modification |
| DELETE | `/objectifs/{id}` | `INITIATIVE_MODIFIER` | Suppression définitive (204) |

Corps (`ObjectifRequest`) :

```json
{ "code": "OBJ-CLOUD", "libelle": "Moderniser vers le cloud", "description": "…" }
```

Réponse (`ObjectifDto`) :

```json
{ "idObjectif": 3, "code": "OBJ-CLOUD", "libelle": "Moderniser vers le cloud", "description": "…" }
```

**Suppression** : `204` si l'objectif est supprimé, `404` si l'id
n'existe pas, `400` avec message détaillé s'il est référencé par au
moins une initiative (**archivées comprises** — le serveur compte toutes
les initiatives rattachées). La clé étrangère `initiative.id_objectif`
est en `ON DELETE SET NULL` : sans ce garde-fou, MySQL détacherait
silencieusement les initiatives et le portefeuille perdrait son
alignement stratégique. On détache d'abord (champ « Objectif » de
l'initiative), on supprime ensuite.

Rattachement : une initiative est liée à un objectif via `idObjectif` (nullable) dans
`InitiativeRequest`. Le filtre `GET /initiatives?objectif={id}` restreint la liste.

---

## 11. Demandes d'initiatives (workflow SPM)

Le workflow est à états contrôlés côté backend :

```
Brouillon → Soumise → En évaluation → Acceptée → (initiative créée, code DEM-xxxxxx)
                          └─────────→ Refusée (motif facultatif)
```

La page propose en outre une bascule **« Actives | Archives »** (`?archive=1`
dans l'URL) : l'archivage masque une demande de la liste active **sans changer
son statut** (comme pour les initiatives), et reste réversible.

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/demandes` | `DEMANDE_SOUMETTRE` ou `DEMANDE_EVALUER` | Liste des demandes — `?recherche=` filtre sur le titre, la description du besoin et la justification (jokers `%…%`, insensible à la casse et aux accents via `utf8mb4_unicode_ci`) ; `?archive=true` bascule sur la vue « Archives ». Les deux vues sont **exclusives** : sans paramètre, seules les demandes **actives** reviennent |
| GET | `/demandes/{id}` | idem | Détail d'une demande |
| DELETE | `/demandes/{id}` | `DEMANDE_EVALUER` | Suppression définitive (**400** si la demande est déjà transformée en initiative) — utilisable depuis les deux vues (purge depuis les archives) |
| PATCH | `/demandes/{id}/archive` | `DEMANDE_SOUMETTRE` ou `DEMANDE_EVALUER` | Archivage vers la vue « Archives » — **400** si la demande n'est pas « Acceptée » ou « Refusée » (jamais une demande en cours) ; idempotent. L'archivage n'est **pas** un statut : le workflow lui-même ne change pas |
| PATCH | `/demandes/{id}/restaurer` | idem | Retour dans la vue « Actives » — réversible à tout moment, aucune condition de statut |
| POST | `/demandes` | `DEMANDE_SOUMETTRE` | Création en « Brouillon » (demandeur = utilisateur connecté) |
| PUT | `/demandes/{id}` | `DEMANDE_SOUMETTRE` | Modification (uniquement en « Brouillon ») |
| POST | `/demandes/{id}/soumettre` | `DEMANDE_SOUMETTRE` | Brouillon → Soumise |
| POST | `/demandes/{id}/evaluer` | `DEMANDE_EVALUER` | Soumise → En évaluation |
| POST | `/demandes/{id}/accepter` | `DEMANDE_EVALUER` | En évaluation → Acceptée |
| POST | `/demandes/{id}/refuser` | `DEMANDE_EVALUER` | En évaluation → Refusée (`{ "motifRefus": "…" }` facultatif) |
| POST | `/demandes/{id}/transformer` | `DEMANDE_EVALUER` | Acceptée → initiative liée (code `DEM-%06d`, aucune création en double) |

Corps (`DemandeRequest`) :

```json
{
  "titre": "Refonte du portail RH",
  "descriptionBesoin": "…",
  "justificationMetier": "…",
  "idEntite": 1,
  "idPriorite": 3
}
```

Réponse (`DemandeResponse`) — extraits :

```json
{
  "idDemande": 1,
  "code": "DEM-000001",
  "titre": "…",
  "statut": { "idStatutDemande": 1, "libelle": "Brouillon" },
  "demandeur": { "idUser": 1, "nomComplet": "…" },
  "motifRefus": null,
  "initiativeLiee": null
}
```

Règles contrôlées par le service :
- chaque transition n'accepte que le statut précédent exact (sinon **400**) ;
- la transformation d'une demande **non acceptée** ou **déjà transformée** renvoie **400** ;
- la suppression d'une demande **déjà transformée** renvoie **400** (elle a
  engendré une initiative avec étapes, budget et historique) ;
- le code `DEM-%06d` est dérivé de l'identifiant de la demande (unicité garantie).

## 12. Assistant IA « Demande à ton portefeuille »

| Méthode | Chemin | Droit | Description |
|---|---|---|---|
| `GET` | `/ia/statut` | tout utilisateur connecté | Disponibilité de l'assistant (ouvert sans droit IA : sert au bandeau et aux tests) |
| `POST` | `/ia/assistant` | `IA_UTILISER` | Question libre à l'assistant |
| `POST` | `/ia/resume/{idInitiative}` | `IA_UTILISER` | Résumé généré d'une initiative (contexte reconstruit en base) |
| `GET` | `/ia/journal` | `UTILISATEUR_GERER` | 100 dernières lignes du journal `journal_ia` |

Exemple :

```http
POST /api/ia/assistant
Authorization: Bearer <jeton>
Content-Type: application/json

{ "question": "Quelles initiatives sont en retard ?", "langue": "fr" }
```

```json
{
  "reponse": "…",
  "modele": "claude-haiku-4-5",
  "dureeMs": 842
}
```

Comportement selon la configuration (`application.yml` → `app.ia.*`, variables
`IA_ACTIF`, `IA_FORMAT`, `IA_BASE_URL`, `IA_MODELE`, `ANTHROPIC_API_KEY`) :

- **configuré** : appel du modèle selon le format — `anthropic` →
  `POST {url}/v1/messages` (clé obligatoire) ; `openai` →
  `POST {url}/v1/chat/completions` (compatible **Ollama local sans clé**,
  LM Studio, Groq, Mistral, OpenRouter…) — réponse 200 ;
- **clé absente en mode `anthropic`, ou `IA_ACTIF=false`** : **503** avec
  message actionnable (« renseignez ANTHROPIC_API_KEY » / « désactivé par
  l'administrateur ») + ligne `NON_DISPONIBLE` au journal ;
- **échec du service (429, réseau…)** : **503** avec message traduit +
  ligne `ERREUR` au journal.

Le journal (`GET /ia/journal`) renvoie les 100 dernières lignes :
`UTILISATEUR`, `TYPE_QUESTION` (`LIBRE`/`RESUME`), `INITIATIVE_CONSULTEE`,
`MODELE`, `STATUT` (`OK`/`NON_DISPONIBLE`/`ERREUR`), `DUREE_MS`,
`MESSAGE_ERREUR` (jamais la clé API).

Points d'assurance :
- **grounding strict** : le contexte (initiatives, jalons, santé) est
  reconstruit depuis la base à chaque requête ; le prompt système interdit
  d'inventer et impose la langue demandée (fr/en/ar/es) + mention loi 09-08 /
  CNDP ;
- **kill switch** : `IA_ACTIF=false` désactive tout (statut, chat, résumé)
  même avec une clé présente ;
- **aucune donnée sensible** : la clé n'est ni journalisée ni renvoyée ;
  seules les questions et métadonnées (modèle, durée, statut) sont tracées.

## 13. Historique des modifications (journal immuable)

Le journal `historique_modification` enregistre **chaque changement champ à
champ** de toute donnée suivie (initiative, étape, jalon, demande,
utilisateur, rôle) : `champ`, `ancienneValeur`, `nouvelleValeur`, auteur,
horodatage et origine (`automatique` = moteur de règles, sinon manuel).
Il remplace l'ancienne table `historique_statut` (migration `INSERT … SELECT`
puis `DROP` au premier démarrage — les 50 lignes de démonstration ont été
reprises sans perte).

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/historique` | `INITIATIVE_CONSULTER` | Liste paginée, en-tête `X-Total-Count` = total hors pagination |
| GET | `/historique/auteurs` | `INITIATIVE_CONSULTER` | Auteurs déjà présents dans le journal (options du filtre déroulant) |
| GET | `/historique/export` | `INITIATIVE_CONSULTER` | Export CSV de **toutes** les lignes filtrées (BOM UTF-8, `;`, en-tête `Date;Objet;…`, `text/csv;charset=UTF-8`, `historique-modifications.csv`) |
| GET | `/initiatives/{id}/historique` | `INITIATIVE_CONSULTER` | Journal de la fiche : filtre `type` ∈ `initiative`/`etape`/`jalon` (autre valeur → **400**), `page`/`size`, `X-Total-Count` |

Paramètres de `GET /historique` (tous facultatifs) :

| Paramètre | Type | Exemple |
|---|---|---|
| `recherche` | `string` | `recherche=MOB` — code ou nom de l'initiative (jokers `%…%`) |
| `type` | `string` | `type=etape` — type d'objet (`initiative`, `etape`, `jalon`, `demande`, `utilisateur`, `role`) ; valeur inconnue → **400** |
| `champ` | `string` | `champ=avancement` — nom du champ modifié |
| `utilisateur` | `long` | `utilisateur=1` |
| `initiative` | `long` | `initiative=3` — filtre posé par le lien « Voir tout » de la fiche |
| `du` / `au` | `date` | `du=2026-01-01&au=2026-12-31` (ISO ; `du > au` → **400**) |
| `archives` | `boolean` | `archives=true` — vue « archives » (événements de plus de 12 mois) ; les deux vues forment une partition du journal |
| `direction` | `string` | `asc` / `desc` (défaut `desc`) ; valeur inconnue → **400** |
| `page`, `size` | `int` | pagination (`page` à partir de 0) |

**Journal immuable** : aucun `POST`, `PUT`, `PATCH` ni `DELETE` n'est déclaré
sur cette ressource — `POST`/`DELETE` sur la collection renvoient **405**,
`DELETE /{id}` renvoie **404** (aucune route), l'anonyme est rejeté en **401**.
Seule l'écriture métier (services de domaine, même transaction que la
modification) y ajoute des lignes. Deux garde-fous de justesse : les valeurs
strictement identiques après normalisation (vide = nul, espaces de bord
retirés, `BigDecimal` sans zéros de queue) ne produisent **jamais** de ligne
parasite, et les valeurs sont tronquées à la taille de colonne.

---

## 14. Historique des connexions (journal des accès)

Chaque passage par `POST /auth/login` dépose une ligne dans la table
`historique_connexion` — **réussie ou échec** (mauvais mot de passe,
compte désactivé, verrouillage 429), avec l'adresse IP et le motif. C'est
le journal de contrôle d'accès attendu d'un outil de pilotage partagé
(loi 09-08).

**Tout accès est réservé aux administrateurs** (`UTILISATEUR_GERER` sur
`/historique-connexions/**`, tous verbes) : le journal expose e-mails et
adresses IP. Aucun nouveau droit n'est créé ; aucun `POST` ni `PUT`
n'existe sur la ressource.

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/historique-connexions` | `UTILISATEUR_GERER` | Liste paginée (tri fixe : plus récent d'abord), en-tête `X-Total-Count` = total hors pagination |
| DELETE | `/historique-connexions/{id}` | `UTILISATEUR_GERER` | Suppression d'une ligne, **404** si absente |
| DELETE | `/historique-connexions` | `UTILISATEUR_GERER` | **Purge totale** (bouton « Purger tout ») : un seul `DELETE` en base via `deleteAllInBatch()` |

Paramètres de `GET /historique-connexions` (tous facultatifs) :

| Paramètre | Type | Exemple |
|---|---|---|
| `recherche` | `string` | `recherche=admin@it6.ma` — e-mail, IP ou nom (jokers `%…%`) |
| `succes` | `boolean` | `succes=false` — tentatives échouées seulement |
| `du` / `au` | `date` | `du=2026-01-01&au=2026-12-31` (ISO ; `du > au` → **400**) |
| `page`, `size` | `int` | pagination (`page` à partir de 0, `size` plafonné à 100) |

L'e-mail saisi est copié en clair, indépendamment du compte : une
tentative sur une adresse inexistante reste traçable. La clé étrangère
`id_utilisateur` est en `ON DELETE SET NULL` : la suppression d'un
compte n'efface pas le journal de ses tentatives.

---

## 15. Risques (registre par initiative et registre global)

Chaque initiative porte un **registre de risques** : titre, description,
**probabilité** et **impact** notés de **1 à 5** (Très faible → Très élevé),
statut (`ouverte` par défaut, `attenuation`, `close`), échéance, plan
d'action et responsable.

Le **score** (`probabilité × impact`, soit **1 à 25**) est **recalculé à
chaque lecture** — jamais stocké — et renvoyé avec un `niveau` aux codes de
`SanteDto` :

| Score | Niveau |
|---|---|
| 1-4 | `VERTE` |
| 5-9 | `AMBRE` |
| 10-14 | `ORANGE` |
| 15-25 | `ROUGE` (**critique** — `SEUIL_CRITIQUE` = 15) |

**Lecture** (registre global, export et registre par initiative) :
`INITIATIVE_CONSULTER`.
**Écriture** : `RISQUE_GERER` (permission **19**, créée pour ce lot,
détenue par l'administrateur **et** le responsable) — noter et suivre un
risque est un métier à part entière, distinct de la simple modification de
l'initiative.

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/risques` | `INITIATIVE_CONSULTER` | Registre **global** (toutes initiatives), paginé, en-tête `X-Total-Count` |
| GET | `/risques/export` | `INITIATIVE_CONSULTER` | Export CSV de la sélection filtrée (toutes les lignes, `risques.csv`) |
| GET | `/initiatives/{id}/risques` | `INITIATIVE_CONSULTER` | Registre de la fiche, **trié du plus critique au moins critique** (score décroissant) |
| POST | `/initiatives/{id}/risques` | `RISQUE_GERER` | Création → `201` avec `score`/`niveau` calculés |
| PUT | `/risques/{id}` | `RISQUE_GERER` | Modification : les nouvelles notes recalculent immédiatement `score`/`niveau` |
| DELETE | `/risques/{id}` | `RISQUE_GERER` | Suppression → `204` |

Paramètres de `GET /risques` (et de `/risques/export`, sans pagination) :

| Paramètre | Type | Exemple |
|---|---|---|
| `initiative` | `long` | `initiative=3` — filtre par initiative |
| `statut` | `string` | `statut=ouverte` — `ouverte`, `attenuation` ou `close` ; valeur inconnue → **aucun filtre** (plutôt que tout masquer) |
| `recherche` | `string` | `recherche=fournisseur` — titre ou description (jokers `%…%`) |
| `page`, `size` | `int` | défaut `page=0`, `size=25`, plafond `200` |

**Corps** (création et modification, `RisqueRequest`) :

```json
{
  "titre": "Fournisseur unique en retard",
  "description": "Fournisseur unique en retard",
  "statut": "ouverte",
  "echeance": "2026-12-31",
  "probabilite": 4,
  "impact": 3,
  "planAction": "Contracter un second fournisseur",
  "idResponsable": 2
}
```

`titre` et `description` sont obligatoires (titre ≤ 150 caractères) ;
`probabilite` et `impact` sont **bornés 1-5** (`@Min`/`@Max`, messages en
français affichés tels quels sous le formulaire) ; `statut` vaut `ouverte`
s'il est absent du corps ; `echeance`, `planAction` et `idResponsable` sont
facultatifs (un plan vidé est stocké `null`, pas `""`).

**Garde-fous** (alignés sur les étapes) :

| Cas | Réponse |
|---|---|
| Initiative inexistante | `404` |
| Risque inexistant (`PUT`/`DELETE`) | `404` |
| Responsable inexistant | `404` |
| **Écriture sur initiative archivée** | `400` (« L'initiative est archivée : … refusé(e) ») |
| Note hors 1-5, titre/description vide, statut hors valeurs | `400` |
| Lecture sur initiative archivée | `200` (le registre reste consultable) |

**Tableau de bord** : `GET /dashboard` renvoie `risquesCritiques[]` — les
risques des initiatives **actives** de la sélection dont `score ≥ 15`, avec
`idInitiative`/`codeInitiative`/`nomInitiative` embarqués (widget
« Risques critiques »). Le KPI « initiatives à risque », lui, suit la
**santé de l'initiative** (`ORANGE` ou `ROUGE`).

---

## 16. Commentaires d'initiative

Fil de discussion par initiative, horodaté et signé du compte connecté.

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/initiatives/{id}/commentaires` | `INITIATIVE_CONSULTER` | Liste chronologique (rattachée à la règle générale `GET /api/initiatives/**`) |
| POST | `/initiatives/{id}/commentaires` | `COMMENTAIRE_AJOUTER` | Ajout → `201`, auteur = compte connecté |
| DELETE | `/commentaires/{id}` | `COMMENTAIRE_AJOUTER` + auteur | `204` — **l'auteur lui-même**, ou un compte détenteur de `UTILISATEUR_GERER` ; sinon `403` |

**Corps** (`CommentaireRequest`) : `{ "contenu": "…" }` — `contenu`
obligatoire (`@NotBlank`).

**Réponse** (`CommentaireResponse`) : `idCommentaire`, `idInitiative`,
`contenu`, `dateCreation`, `auteur` (`idUser`, `nomComplet`, `e-mail`).

L'auteur est résolu depuis le jeton (e-mail) puis rechargé en base — le
compte peut avoir changé de nom entre-temps. **Écriture sur initiative
archivée → 400** (« le dossier est clos ») ; la lecture reste ouverte. Si
le compte de l'auteur disparaît, son nom est anonymisé dans le journal
(même comportement que l'historique des modifications).

---

## 17. Dépenses (suivi budgétaire)

Les dépenses d'initiative sont une **information financière** : **tous les
verbes — lecture ET écriture — exigent `DEPENSE_SAISIR`** (permission
**20**, admin + responsable). La règle est posée dans `SecurityConfig`
**avant** la règle générale `GET /api/initiatives/**` : lire les dépenses
d'une initiative n'est pas un corollaire de lire l'initiative. Le visiteur
(détenteur de `INITIATIVE_CONSULTER` sans `DEPENSE_SAISIR`) reçoit donc
**403** sur ces routes.

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/depenses` | `DEPENSE_SAISIR` | Registre global paginé — en-têtes `X-Total-Count` (nombre) **et** `X-Total-Montant` (somme de la sélection) |
| GET | `/depenses/export` | `DEPENSE_SAISIR` | Export CSV de la sélection (toutes les lignes, `depenses.csv`) |
| GET | `/initiatives/{id}/depenses` | `DEPENSE_SAISIR` | Registre de la fiche (les plus récentes d'abord), hors pagination |
| POST | `/initiatives/{id}/depenses` | `DEPENSE_SAISIR` | Création → `201`, saisisseur = compte connecté |
| PUT | `/depenses/{id}` | `DEPENSE_SAISIR` | Modification |
| DELETE | `/depenses/{id}` | `DEPENSE_SAISIR` | Suppression → `204` |

Paramètres de `GET /depenses` (et de `/depenses/export`, sans pagination) :

| Paramètre | Type | Exemple |
|---|---|---|
| `initiative` | `long` | `initiative=3` |
| `categorie` | `string` | `categorie=Externalisation` |
| `du` / `au` | `date` | `du=2026-01-01&au=2026-12-31` (ISO) |
| `recherche` | `string` | `recherche=mobilier` — libellé (jokers `%…%`) |
| `page`, `size` | `int` | défaut `25`, plafond `200` |

**Corps** (`DepenseRequest`) : `libelle` (obligatoire), `montant`
(`@Positive` — strictement positif), `dateDepense` (`@NotNull`, ISO),
`categorie` (≤ 50 caractères).

**Réponse** (`DepenseResponse`) : `idDepense`, `idInitiative`,
`codeInitiative`, `nomInitiative`, `libelle`, `montant`, `dateDepense`,
`categorie`, `dateCreation`, `saisisseur`.

**Garde-fous** : initiative ou dépense inexistante → `404` ; **écriture sur
initiative archivée → 400** ; montant nul ou négatif, date ou libellé
absents → `400`.

---

## 18. Alertes (calculées à la lecture — sans table)

Aucune table ne stocke les alertes : `AlerteService` les **recalcule à
chaque lecture** à partir des initiatives, jalons, risques, demandes et
budgets du moment. Deux routes :

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/notifications` | tout compte connecté | `{ "total": n, "alertes": [...] }` — alimente la cloche du bandeau (rafraîchie toutes les 60 s par le frontend) |
| GET | `/initiatives/{id}/alertes` | `INITIATIVE_CONSULTER` | Bloc « Alertes » de la fiche de suivi |

**`AlerteDto`** : `type`, `niveau` (`ROUGE` | `ORANGE`), `idInitiative`,
`idDemande`, `reference` (code de l'initiative ou de la demande),
`libelle`, `echeance`, `parametres` (objet de valeurs à interpoler dans le
message : `jours`, `score`, `nom`, `budget`, `consomme`…).

Les 7 types :

| `type` | Niveau | Déclencheur |
|---|---|---|
| `initiativeEnRetard` | `ROUGE` | date de fin prévue dépassée |
| `jalonEnRetard` | `ROUGE` | jalon prévu dépassé |
| `jalonBientot` | `ORANGE` | jalon prévu dans les 7 jours |
| `risqueCritique` | `ROUGE` | risque au score ≥ 15 |
| `risqueEcheance` | `ORANGE` | échéance de risque dans les 7 jours |
| `demandeEnAttente` | `ORANGE` | demande « En évaluation » en attente d'arbitrage |
| `budgetDepasse` | `ROUGE` | dépenses consommées supérieures au budget |

---

## 19. Mon espace

`GET /mon-espace` (tout compte connecté) renvoie l'**agrégat personnel** :
le profil, ce que le compte pilote et ce qui l'attend. Le contenu
**s'adapte aux permissions** : les chiffres budgétaires ne sont produits que
pour un compte détenteur de `BUDGET_CONSULTER`, les demandes à évaluer que
pour `DEMANDE_EVALUER`, etc.

**`MonEspaceResponse`** : `compte` (résumé du compte), `role`,
`initiativesPilotees[]`, `jalonsProches[]`, `mesRisques[]` (risques où le
compte est responsable), `alertes[]`, `activiteRecente[]` (journal des
modifications), `demandesEnAttente[]`, `budgetTotal`, `depensesTotales`.

---

## 20. Journal d'audit des actions HTTP

Le **filtre d'audit** (`AuditFilter`, `FilterRegistrationBean` ordre 0,
exécuté dans la chaîne de sécurité après le filtre JWT) journalise chaque
**écriture** HTTP dans `journal_audit` : horodatage, compte, IP, méthode,
route (avec query, tronquée à 300 caractères), statut HTTP et corps
(tronqué à 300 caractères). C'est le journal de contrôle d'accès côté
actions (loi 09-08), distinct de l'historique métier (section 13) et de
l'historique des connexions (section 14).

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/journal-audit` | `UTILISATEUR_GERER` | Liste paginée, en-tête `X-Total-Count` |
| GET | `/journal-audit/export` | `UTILISATEUR_GERER` | Export CSV des lignes filtrées (`journal-audit.csv`) |

Paramètres (communs aux deux routes) :

| Paramètre | Type | Exemple |
|---|---|---|
| `email` | `string` | `email=admin@it6.ma` |
| `methode` | `string` | `methode=POST` |
| `route` | `string` | `route=%2Fapi%2Finitiatives%` — jokers `%…%` |
| `du` / `au` | `date` | `du=2026-01-01&au=2026-12-31` (ISO) |
| `page`, `size` | `int` | défaut `25`, plafond `200` (liste seulement) |

**Journal immuable** : aucun `POST`/`PUT`/`PATCH`/`DELETE` n'est déclaré
sur la ressource — ces verbes renvoient **405**.

**Garde-fous du filtre** :

- seules les **écritures** (`POST`/`PUT`/`PATCH`/`DELETE`) sont retenues —
  le `GET` serait du bruit ;
- les **champs de mot de passe** (`motDePasse`, `nouveauMotDePasse`,
  `motDePasseActuel`, `password`, `secret`, `token`) sont **masqués**
  (`"***"`) dans le corps conservé ;
- la mise à jour nue `PUT /api/initiatives/{id}` est **ignorée** : son
  champ par champ est déjà tracé par le journal des modifications ;
- les rejets de sécurité (401/403) n'atteignent jamais le filtre — ils
  sont tracés côté connexions ;
- l'enregistrement est *best-effort* : une erreur d'audit est journalisée
  en log sans jamais faire échouer l'action métier ;
- l'initialisation des données de démonstration ne passe pas par l'HTTP et
  n'y laisse rien ; `database/cleanup-test-data.sql` vide le journal entre
  deux exécutions de la suite de tests.
