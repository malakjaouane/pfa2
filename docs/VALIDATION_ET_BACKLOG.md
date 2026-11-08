# Validation & Backlog — Portefeuille d'initiatives digitales (IT6)
## À destination de l'encadrant

> Ce document est une **ébauche** à compléter dès réception de la liste
> des recommandations de l'encadrant. Il recense l'état de livraison,
> les écarts identifiés et le reste à faire.

---

## 1. État des livraisons

### Phase 1 — Sécurisation & authentification moderne (livrée)

| Sous-lot | Contenu | Statut |
|----------|---------|--------|
| A. Flyway | Schéma unique `database/migration/V*.sql` (baseline V1..V11), `ddl-auto: validate`, suppression `schema.sql`/`data.sql` | ✅ Commité |
| B1. Secrets & comptes | `ADMIN_PASSWORD` obligatoire (refus si vide), avertissement si `Admin@123`, `VerificationSecrets`, `application-dev.yml` (démo uniquement), `Profils.java`, conteneurs en profil `conteneur` | ✅ Commité |
| B2. Changement mdp imposé | Colonne `doit_changer_mot_de_passe` (V3), filtre V3 (403 `MOT_DE_PASSE_A_CHANGER`), écran `/mot-de-passe` i18n fr/en/ar/es + RTL, cloche silencieuse | ✅ Commité |
| C1. Cookies HttpOnly + refresh | Access 15 min + refresh 7 jours tournants, SHA-256 stocké, révocation vol/déconnexion/mdp, `COOKIE_SECURE` pilotable | ✅ Commité |
| C2. CSRF + CORS | `CookieCsrfTokenRepository` (XSRF-TOKEN), header `X-XSRF-TOKEN`, CSRF uniquement pour requêtes cookie, CORS credentials | ✅ Commité |
| C3. Rate-limit persistant | Table `tentative_connexion` (V9), compteurs par compte + IP, purge planifiée | ✅ Commité |
| D1. Actuator | `/actuator/health`, `/actuator/info` (réservé admin) | ✅ Commité |
| D2. Springdoc | `/api/docs`, `/swagger-ui.html` (réservé admin) | ✅ Commité |
| D3. Compose prod + Caddy | `docker-compose.prod.yml`, secrets exigés `${VAR:?}`, Caddy reverse proxy + headers sécurité + TLS auto | ✅ Commité |
| D4. Sauvegarde | `database/backup-database.sh` (mysqldump + rotation) | ✅ Commité |
| E1. Vitest + RTL | 3 tests front (LoginPage, OnboardingPage), `vitest run` vert | ✅ Commité |
| E2. CI GitHub Actions | `.github/workflows/ci.yml` (Java 17 + mvn test, Node 20 + npm test + build) | ✅ Commité |
| E3. Testcontainers | Test d'infrastructure MySQL conteneurisé (`TestcontainersInfrastructureTest`) | ✅ Commité |

### Phase 2 — SaaS multi-organisation (livrée)

| Sous-lot | Contenu | Statut |
|----------|---------|--------|
| 2A. Modèle + isolation | Entité `Organisation`, `id_organisation` sur 11 tables + 3 journaux (V5), filtre Hibernate `@Filter` centralisé, contexte serveur | ✅ Commité |
| 2B1. Service e-mail | `spring-boot-starter-mail`, `SmtpEmailService` (mode log si pas SMTP), Mailpit dans compose, modèles fr/en/ar/es | ✅ Commité |
| 2B2. Inscription + vérif e-mail | V6 (`email_verifie`, `jeton_verification` 24 h), `POST /api/auth/signup`, `GET /api/auth/verifier-email`, 403 `EMAIL_NON_VERIFIE` | ✅ Commité |
| 2B3. Mot de passe oublié | V7 (`jeton_reinitialisation` 1 h), `POST /api/auth/mot-de-passe-oublie` (200 toujours), `POST /api/auth/reinitialiser-mot-de-passe` | ✅ Commité |
| 2B4. Invitations | V8 (`membre_invitation` 7 j), `POST /api/auth/inviter` (admin uniquement), `POST /api/auth/accepter-invitation` | ✅ Commité |
| 2B5. Onboarding front | `/inscription`, `/onboarding` (3 étapes), redirection auto depuis `AuthContext`, i18n fr/en/ar/es | ✅ Commité |
| 2C. Droits par initiative | V10 (`membre_initiative`), entité + repository + service, contrôle écriture dans `InitiativeController` (responsable ou membre contributeur) | ✅ Commité |
| 2D. Plans | V11 (`plan` gratuit/pro), entité + repository + service, contrôle limite d'initiatives à la création | ✅ Commité |

---

## 2. Démo conservée (intacte)

- 3 comptes : `admin@it6.ma`, `responsable@it6.ma`, `visiteur@it6.ma`
- 6 initiatives dont `DIG-005` archivée
- 3 objectifs, 52 lignes d'historique, 1 demande `DEM-000001`
- 0 résidu `TEST*` (purgé)
- Migrations appliquées : baseline(2) + V3..V11

---

## 3. Écart vs exigences (à valider avec l'encadrant)

| # | Sujet | État actuel | Écarts / questions ouvertes |
|---|-------|-------------|------------------------------|
| 1 | **Journal immuable vs purge** | Le journal (`historique_modification`, `journal_audit`, `journal_ia`) est immuable : aucune suppression n'est exposée. Les lignes > 12 mois quittent la vue active par calcul de date, jamais de destruction. | L'exigence évoque une purge historique. À confirmer : conserver l'immuabilité (recommandé) ou ajouter une purge archivante. |
| 2 | **Comptes démo / `Admin@123`** | `Admin@123` retiré de `application.yml` ; valeur de démo uniquement dans `application-dev.yml` et `.env` local. Le admin créé hors dev doit changer son mdp au premier login. | Les comptes démo utilisent encore `Admin@123`/`Resp@123`/`Visi@123` pour la démonstration. À confirmer si acceptable en l'état. |
| 3 | **CDC v1** | Hors périmètre (pas d'équipe multi-membres, pas de pièces jointes, pas de dépendances, pas de droits par initiative au-delà de `membre_initiatique`, pas de SSO, pas de cookie HttpOnly séparé — déjà fait, pas d'export Excel/PDF). | À confirmer si certains items CDC doivent être réintégrés. |
| 4 | **Menu 11/12** | Incohérence signalée entre le nombre de menu items (11 vs 12 selon le rôle). | À vérifier/corriger côté front. |
| 5 | **Droits par initiative** | Contrôle de l'écriture (modifier/supprimer/changer statut) implémenté via `membre_initiative`. La lecture reste filtrée par organisation (isolation 2A). | À confirmer si un contrôle de lecture par membre est nécessaire. |
| 6 | **Plans** | Limites vérifiées uniquement à la création d'initiative. Pas d'interface de changement de plan (pas de paiement). | À confirmer si une UI de changement de plan est nécessaire. |

---

## 4. Reste à faire (backlog)

1. [ ] Recevoir la liste des recommandations de l'encadrant et mettre à jour ce document.
2. [ ] Valider l'option journal immuable (conserver) vs purge (écart #1).
3. [ ] Confirmer le traitement des comptes démo / `Admin@123` (écart #2).
4. [ ] Corriger l'incohérence menu 11/12 (écart #4) côté front.
5. [ ] Éventuellement : interface de changement de plan (écart #6) et CDC v1 (écart #3) si retenus.
6. [ ] Re-tests de navigation end-to-end (login forcé, AR/RTL, cookies) après toute modif front.
7. [ ] Mettre à jour `docs/API.md` (springdoc) si endpoints ajoutés.

---

## 5. Règles impératives respectées (tout le projet)

1. Une sous-lot à la fois, plan court + validation avant de coder.
2. `mvn test` vert après chaque étape ; tests mis à jour si comportement volontaire.
3. Petits commits atomiques, message FR `type: description`.
4. Aucun secret en dur, tout par variable d'environnement (`.env` ignoré par git).
5. Toute chaîne affichée i18n fr/en/ar/es avec RTL correct (vérifié navigateur).
6. Aucune dépendance sans justification d'une phrase (dans les commentaires de `pom.xml`/`package.json`).
7. Multi-tenant par organisation obligatoire, aucune fuite inter-organisations (filtre Hibernate centralisé).
8. Fin de sous-lot = changements / reste à faire / commandes de vérification exactes.
9. Pas d'automatisation des commits/push ; chaque commit+push approuvé.

---

## 6. Commandes de vérification (exactes)

```powershell
# Backend (depuis backend/)
C:\Users\t14\Downloads\pfa2\tools\apache-maven-3.9.16\bin\mvn.cmd -q test   # attendu : 302 tests, 0 échec

# Frontend (depuis frontend/)
powershell -ExecutionPolicy Bypass -Command "npm test"   # attendu : Vitest vert
powershell -ExecutionPolicy Bypass -Command "npm run build"  # attendu : build OK

# Suite API (337 checks)
powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\t14\Downloads\pfa2\database\tests-api.ps1

# Nettoyage résidu TEST*
powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\t14\Downloads\pfa2\database\cleanup-test-data.sql  (ou run-cleanup.ps1)
```

---

*Document préparé par l'agent OpenCode. À finaliser dès réception de la liste des recommandations de l'encadrant.*
