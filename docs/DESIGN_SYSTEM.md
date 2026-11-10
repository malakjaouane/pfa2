# Design System — Phase 3 : refonte du design

> **Statut : validé — fondations implémentées** (tokens clair/sombre, purge
> ServiceNow, code mort supprimé, kit Radix ajouté). Le reste de la phase suit
> ce document.
> Objectif : app moderne et vivante (esprit Linear / Notion / Stripe) avec une
> identité marocaine contemporaine. Fin de l'imitation ServiceNow.

---

## 1. Identité — « Zellige contemporain »

Le socle suit la retenue des produits Linear/Notion/Stripe : base neutre chaude,
**une** couleur de marque forte, filets fins, ombres discrètes, typographie serrée,
beaucoup d'espace. La signature marocaine vient du **bleu Majorelle** (marque),
de l'**ocre safran** (accent), du **turquoise zellige** (surlignage) et du motif
`MotifZellige.jsx` — utilisé avec parcimonie : page de connexion, hero de la
vitrine, fonds vides décoratifs. Jamais sous un formulaire ni derrière du texte dense.

La charte actuelle **navy `#032D42` / cyan `#24C2CE` / vert `#00875A`** (« wasabi »,
ADN ServiceNow) est supprimée (§8).

---

## 2. Tokens

### 2.1 Couleurs — mode clair

| Token | HSL | Usage |
|---|---|---|
| `--background` | `40 33% 98%` (#FBFAF8) | fond de page — blanc chaud |
| `--foreground` | `220 25% 10%` (#17191E) | texte principal |
| `--card` / `--popover` | `0 0% 100%` | cartes, menus |
| `--primary` | `204 100% 32%` (#0067A5) | **Majorelle** — actions primaires |
| `--primary-fg` | `210 40% 98%` | texte sur primaire |
| `--accent` | `28 90% 45%` (#E8820D) | **ocre safran** — surfaces, icônes, badges. Jamais en texte sur clair (variante texte : `26 75% 35%`) |
| `--highlight` | `187 80% 32%` (#0D97AF) | **turquoise** — surlignage, lien secondaire |
| `--muted` / `--muted-fg` | `40 20% 95%` / `220 8% 46%` | fonds secondaires, texte secondaire |
| `--border` / `--input` | `40 15% 90%` | filets, champs |
| `--success` | `152 55% 32%` | vert succès (remplace `#00875A`) |
| `--destructive` | `0 72% 51%` | erreurs, suppression |

### 2.2 Couleurs — mode sombre (classe `.dark`, déjà câblée)

| Token | HSL |
|---|---|
| `--background` | `220 18% 7%` (#101216) |
| `--foreground` | `40 20% 96%` |
| `--card` | `220 16% 10%` |
| `--border` | `220 12% 18%` |
| `--primary` | `204 82% 52%` (Majorelle éclairci) |
| `--accent` | `30 88% 58%` |
| `--muted-fg` | `220 8% 62%` |

**Contrastes** : texte courant ≥ 7:1, texte d'action ≥ 4.5:1 (AA), anneau de focus
visible dans les deux modes — vérifiés au calibrage avec un outil de contraste.

### 2.3 Rayons
`xs 4 · sm 6 · md 8 (défaut) · lg 12 · xl 16 · full`
Cartes et menus/dialogues = lg 12 · boutons, champs et onglets = md 8.

### 2.4 Ombres (logique : filet d'abord, ombre ensuite)

| Jeton | Valeur | Usage |
|---|---|---|
| `xs` | `0 1px 2px rgb(0 0 0/.05)` | cartes au repos |
| `sm` | `0 1px 3px + 0 1px 2px -1px` | cartes survolées |
| `md` | `0 4px 8px -2px` | menus, popovers |
| `lg` | `0 12px 24px -6px` | dialogues, sheet |
| `ring` | anneau 2px `--primary`, offset 2px | focus clavier |

### 2.5 Espacements & grille
Défaut Tailwind (base 4 px) — aucun ajout. Gabarit : sidebar 264 px, contenu
max 1200 px, rythme vertical 24/32 px.

### 2.6 Typographie

| Rôle | Police | Source |
|---|---|---|
| UI, texte, titres | **Inter** 400/500/600 (700 display landing) | locale `public/fonts/inter-var.woff2` — **aucune dépendance** |
| Arabe / RTL | **Cairo** 400/600/700 | locale `cairo-arabic.woff2` + `cairo-latin.woff2` — déjà embarquée, couvre latin + arabe |
| Chiffres (KPI, budgets) | Inter + `font-variant-numeric: tabular-nums` | — |

- Échelle : **12 · 14 (corps dense) · 16 (corps) · 20 · 24 · 30 · 36 (titres) · 48–60 (display landing)**
- Interlignage 1,5 (corps) / 1,2 (titres) ; tracking **−0.02em** sur les titres ≥ 20 px
- `dir="rtl"` → `font-family: var(--font-arabic)` (bascule automatique existante)

### 2.7 Mouvement
- Easing unique : `cubic-bezier(0.16, 1, 0.3, 1)` (expo-out) — durées **120 / 180 / 240 ms**
- `useReducedMotion` (existant) neutralise toute animation
- Framer Motion : entrées `opacity 0→1` + `y 8→0` ; jamais de scale > 1.02

### 2.8 Z-index
`0` base · `40` bottom-nav/dropdowns · `50` sheet/dialogue · `60` toasts · `70` palette Ctrl+K

---

## 3. Composants

### 3.1 Kit `ui/` — existant (12) → cible (+7)
**Existant** : button, badge, card, input, label, separator, skeleton, table, sheet,
empty-state, theme-toggle, toast.
**Ajoutés (shadcn/ui, testés par `src/test/ui-kit.test.jsx`)** : `dialog`,
`dropdown-menu`, `tabs`, `tooltip`, `switch`, `popover`, `select` (primitives
Radix — §11).

### 3.2 Patterns applicatifs
- **Existants à harmoniser** : KpiCard, ProgressBar, StatutBadge, SanteBadge,
  ListeAlertes, NotificationsBell, CommandPalette, KanbanBoard, MotifZellige, LanguageSwitcher
- **Nouveaux** : `SkeletonPage` (squelette par type de page), `EmptyState` enrichi
  (illustration + appel à l'action), `BottomNav` (mobile), helper de mise à jour
  optimiste (Kanban), `Celebration` (clôture de jalon), cartes **MaJournee**

### 3.3 Iconographie
**lucide-react** (déjà installé) pour tout le nouveau code — trait 1,75, tailles 16/20.
`Icons.jsx` (jeu maison) : supprimé page par page, entièrement en fin de phase.

---

## 4. Navigation cible (16 entrées → 5 sections)

1. **Pilotage** — Ma journée *(nouveau)*, Tableau de bord
2. **Portefeuille** — Initiatives *(archives = filtre intégré)*, Roadmap, Demandes, Objectifs
3. **Suivi** — Risques, Dépenses, Commentaires
4. **Activité et sécurité** — **page unique à 4 onglets** : Historique · Connexions · Audit · Journal IA
5. **Administration** - Utilisateurs, Rôles, Entités

Haut de page : recherche Ctrl/Cmd+K, Assistant IA, notifications, profil.
Mobile : **barre de navigation en bas** (5 destinations maximum + « Plus »).

---

## 5. Chantiers d'expérience — résumé

- **« Ma journée »** : retards, jalons de la semaine, actions attendues de moi,
  suggestion de l'assistant IA (une carte, pas un dashboard de plus)
- **Ctrl/Cmd+K** : enrichir l'existant (navigation ✓ déjà) — + recherche d'initiatives
  via API, + actions rapides
- **États** : skeletons de chargement, états vides illustrés + CTA, toasts,
  mises à jour optimistes (changement de statut Kanban)
- **Micro-animations sobres** : compteurs (600 ms ease-out), barres de progression
  (240 ms), transitions de page (fade + 8 px), célébration de jalon (anneau + toast
  « Jalon clôturé » — sobre, sans confettis)

---

## 6. Mobile & PWA

- **Mobile d'abord** : `BottomNav`, tableaux → cartes sous 768 px, cibles tactiles ≥ 44 px
- **PWA** : `manifest.json` + `sw.js` **existent déjà** — reste : icônes 192/512/maskable
  (générées depuis le logo avec System.Drawing — aucune dépendance), page hors-ligne,
  contrôle installable (Lighthouse)

---

## 7. Landing publique

- Refonte complète avec les tokens + `MotifZellige` (hero uniquement)
- Message produit : **« Pilotez votre portefeuille d'initiatives, en français et en
  arabe, conforme à la loi 09-08 »**
- Sections : hero · démos (Kanban/IA existants) · fonctionnalités · **tarifs**
  (Gratuit / Pro / Entreprise) · **FAQ** · CTA final
- **Zéro ressource externe** : polices locales, images `/images`, aucun CDN
  (CSP `connect-src 'self'` déjà en place)

---

## 8. Purge ServiceNow

| Fichier | Action |
|---|---|
| `README.md` §« Charte graphique » + §« Vitrine – inspiration ServiceNow » (L518-572, L609) | Réécriture avec ce design system, suppression de toute référence ServiceNow |
| `backend/.../entity/DemandeInitiative.java` (L9) | Commentaire reformulé sans ServiceNow |
| `backend/.../service/DemandeService.java` (L21, L219) | Idem |
| `database/migration/V1__baseline.sql` (L271) | **Reporté** : migration Flyway déjà appliquée — checksum figé dans `flyway_schema_history`, éditer le fichier casserait le démarrage du backend (voir note) |
| `frontend/src/styles.css` — sections « Charte IT6 », navy/cyan | Supprimées page par page (§9) |

> **Note V1** : contrairement aux autres fichiers, `V1__baseline.sql` ne peut pas
> être édité tant que des bases existantes (dev, test, démo) portent son checksum
> — Flyway échouerait au démarrage (« checksum mismatch »). Neutraliser ce
> commentaire demanderait un `flyway repair` sur chaque base existante : à trancher
> avec un accès base disponible.

**Critère de fin** : `grep -ri "servicenow\|wasabi" README.md backend database frontend` → 0 résultat
(hors `V1__baseline.sql`, figé par Flyway — voir note).

---

## 9. Stratégie `styles.css` (4 216 lignes) — sans big bang

1. **Règle** : un composant = ses styles → **Tailwind par défaut** ; keyframes ou
   styles complexes → fichier CSS porté par le composant, importé uniquement là.
   Aucun style hérité après migration d'une page.
2. **Ordre** :
   a. purge du code mort d'abord (ex. `.kpi-vert` : **0 usage** aujourd'hui),
   b. migration page par page : auth → layout + dashboard → portefeuille → suivi →
      administration,
   c. vitrine en dernier, avec sa refonte (§7).
3. **Preuve par page** : grep des classes de la section → 0 usage → la section disparaît.
4. **Fin de phase** : `styles.css` supprimé (tout résidu doit être listé et justifié).

---

## 10. Définition de fini — checklist par page

- [ ] Mode clair **et** sombre
- [ ] FR / AR (RTL) + EN / ES sans régression
- [ ] Mobile ≤ 768 px (BottomNav, tableaux en cartes)
- [ ] Clavier : tabulation, focus visible, Échap, raccourcis
- [ ] Contraste AA (≥ 4,5:1)
- [ ] `npm run build` + Vitest verts

---

## 11. Dépendances (justification)

- **Aucune nouvelle** pour les fondations : Tailwind CSS, shadcn/ui
  (cva/clsx/tailwind-merge), lucide-react et framer-motion sont déjà installés
  et utilisés.
- **8 packages Radix (installés)** :
  `@radix-ui/react-{slot,dialog,dropdown-menu,tabs,tooltip,switch,popover,select}` —
  *primitives d'accessibilité (piège de focus, ARIA, navigation clavier) fournies
  derrière les composants shadcn/ui, qu'il faudrait sinon réimplémenter à la main.*
- **Polices** : Inter + Cairo déjà embarquées localement — rien à télécharger.
