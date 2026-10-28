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

## 15. Risques (registre par initiative)

Chaque initiative porte un **registre de risques** : description libre,
**probabilité** et **impact** notés de **1 à 3** (Faible / Moyen / Élevé),
responsable du plan d'action et plan d'action.

La **santé pondérée** (`score` = probabilité × impact, soit **1 à 9**) est
**recalculée à chaque lecture** — jamais stockée — et renvoyée avec un
`niveau` aux mêmes codes que `SanteDto` (`VERTE` = 1-2, `ORANGE` = 3-4,
`ROUGE` = 6-9). **Critique = score ≥ 6** (bande rouge alignée sur
`CalculeurSante`).

**Lecture** : `INITIATIVE_CONSULTER`, rattrapée par la règle
`GET /api/initiatives/**` (aucun droit spécifique).
**Écriture** : `INITIATIVE_MODIFIER` — gérer les risques, c'est enrichir le
dossier de l'initiative elle-même (aucun nouveau droit créé).

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/initiatives/{id}/risques` | `INITIATIVE_CONSULTER` | Registre, **trié du plus critique au moins critique** (score décroissant, puis ordre d'arrivée) |
| POST | `/initiatives/{id}/risques` | `INITIATIVE_MODIFIER` | Création → `201` avec `score`/`niveau` calculés |
| PUT | `/risques/{id}` | `INITIATIVE_MODIFIER` | Modification : les nouvelles notes recalculent immédiatement `score`/`niveau` |
| DELETE | `/risques/{id}` | `INITIATIVE_MODIFIER` | Suppression → `204` |

**Corps** (création et modification, `RisqueRequest`) :

```json
{
  "description": "Fournisseur unique en retard",
  "probabilite": 3,
  "impact": 3,
  "planAction": "Contracter un second fournisseur",
  "idResponsable": 2
}
```

`probabilite` et `impact` sont **bornés 1-3** par la validation
(`@Min`/`@Max`, messages en français) ; `description` est obligatoire ;
`planAction` et `idResponsable` sont facultatifs (un plan vidé est stocké
`null`, pas `""`).

**Garde-fous** (alignés sur les étapes) :

| Cas | Réponse |
|---|---|
| Initiative inexistante | `404` |
| Risque inexistant (`PUT`/`DELETE`) | `404` |
| Responsable inexistant | `404` |
| **Écriture sur initiative archivée** | `400` (« L'initiative est archivée : … refusé(e) ») |
| Note hors bornes / description vide | `400` |
| Lecture sur initiative archivée | `200` (le registre reste consultable) |

**Tableau de bord** : `GET /dashboard` renvoie `risquesCritiques[]` — les
risques des initiatives **actives** de la sélection dont `score ≥ 6`, avec
`idInitiative`/`codeInitiative`/`nomInitiative` embarqués (widget
« Risques critiques »). Le widget suit donc les mêmes filtres que les KPI
et se vide dès qu'un risque repasse sous 6/9.
