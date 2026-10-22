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
| GET | `/dashboard` | `DASHBOARD_CONSULTER` | `totalInitiatives, enCours, terminees, enPause, enRetard, budgetTotal, avancementMoyen, parStatut[], parPriorite[], parEntite[], parResponsable[], parObjectif[], initiativesEnRetard[]` |

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
| GET | `/initiatives/{id}/historique-statuts` | `INITIATIVE_CONSULTER` | Historique des changements de statut |
| PATCH | `/initiatives/{id}/archive` | `INITIATIVE_ARCHIVER` | Archivage (réservé à « Terminée » / « Annulée », RC03) |

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

Corps (`ObjectifRequest`) :

```json
{ "code": "OBJ-CLOUD", "libelle": "Moderniser vers le cloud", "description": "…" }
```

Réponse (`ObjectifDto`) :

```json
{ "idObjectif": 3, "code": "OBJ-CLOUD", "libelle": "Moderniser vers le cloud", "description": "…" }
```

Rattachement : une initiative est liée à un objectif via `idObjectif` (nullable) dans
`InitiativeRequest`. Le filtre `GET /initiatives?objectif={id}` restreint la liste.

---

## 11. Demandes d'initiatives (workflow SPM)

Le workflow est à états contrôlés côté backend :

```
Brouillon → Soumise → En évaluation → Acceptée → (initiative créée, code DEM-xxxxxx)
                          └─────────→ Refusée (motif facultatif)
```

| Méthode | Route | Permission | Description |
|---|---|---|---|
| GET | `/demandes` | `DEMANDE_SOUMETTRE` ou `DEMANDE_EVALUER` | Liste des demandes |
| GET | `/demandes/{id}` | idem | Détail d'une demande |
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
- le code `DEM-%06d` est dérivé de l'identifiant de la demande (unicité garantie).

## 12. Assistant IA (� Demande � ton portefeuille �)

L'assistant r�pond **uniquement** � partir d'un contexte reconstruit � chaque requ�te
(initiatives non archiv�es : statut, avancement, sant�, budget, responsable) � le
mod�le n'a jamais d'acc�s libre, et le prompt syst�me lui interdit d'inventer
(la loi 09-08 / CNDP). **Chaque requ�te est journalis�e** (`journal_ia` : auteur,
demande, r�ponse, statut, dur�e).

| Endpoint | Permission | R�le |
|---|---|---|
| `GET /ia/statut` | connect� | Disponibilit� (`actif`, `cleRenseignee`, `disponible`, `modele`) � permet � l'interface d'afficher � indisponible � explicitement |
| `POST /ia/assistant` | `IA_UTILISER` (17) | Question conversationnelle |
| `POST /ia/resume/{idInitiative}` | `IA_UTILISER` (17) | R�sum� d'une initiative (fiche + �tapes + jalons) |
| `GET /ia/journal` | `UTILISATEUR_GERER` | Journal des requ�tes (administrateurs) |

Corps de `POST /ia/assistant` (`AssistantIaRequest`) :

```json
{ "question": "Quelles initiatives risquent de d�passer leur budget ?", "langue": "fr" }
```

R�ponse (`AssistantIaResponse`) :

```json
{ "reponse": "�", "modele": "claude-haiku-4-5", "dureeMs": 842 }
```

Comportement selon la configuration (`application.yml` ? `app.ia.*`, variables
`ANTHROPIC_API_KEY`, `IA_ACTIF`, `ANTHROPIC_MODELE`) :

- **configur�** : appel Anthropic (`POST {url}/v1/messages`), r�ponse 200 ;
- **cl� absente ou `IA_ACTIF=false`** : **503** avec message actionnable
  (� renseignez ANTHROPIC_API_KEY � / � d�sactiv� par l'administrateur �) +
  ligne `NON_DISPONIBLE` au journal ;
- **�chec du service (429, r�seau�)** : **503** avec message traduit +
  ligne `ERREUR` au journal.

Le journal (`GET /ia/journal`) renvoie les 100 derni�res lignes :
`{ id, dateRequete, type: QUESTION|RESUME, demande, reponse, statut: OK|ERREUR|NON_DISPONIBLE, modele, dureeMs, initiativeCode, utilisateurEmail }`.
