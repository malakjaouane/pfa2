# Portefeuille d'initiatives digitales — IT6 Consulting

## Diagrammes UML corrigés (version Mermaid)

> **Source de vérité.** Ces diagrammes sont reconstruits à partir du **code réel**
> (entités JPA, services, contrôleurs, `SecurityConfig`, `schema.sql`) et non des
> croquis initiaux. Ils reflètent donc exactement l'application livrée.
>
> Chaque diagramme est fourni en **Mermaid** (ci-dessous, rendu par GitHub/IDE) et
> en **PlantUML** (dossier `docs/UML/plantuml/`).

---

## 1. Diagramme de cas d'utilisation

Trois acteurs selon le cahier des charges. Les profils sont **hiérarchiques** :
les droits du Visiteur sont inclus dans ceux du Responsable, eux-mêmes inclus
dans ceux de l'Administrateur.

```mermaid
flowchart LR
    Visiteur(("Visiteur"))
    Responsable(("Responsable"))
    Admin(("Administrateur"))

    subgraph SYS["Portefeuille d'initiatives digitales"]
        UC_AUTH(["S'authentifier"])
        UC_CONSULT(["Consulter les initiatives<br/>(recherche & filtres)"])
        UC_DETAIL(["Consulter une initiative,<br/>ses étapes et ses jalons"])
        UC_DASH(["Consulter le dashboard<br/>et les KPI"])
        UC_CREER(["Créer une initiative"])
        UC_MODIF(["Modifier une initiative<br/>(avancement, affectation)"])
        UC_STATUT(["Faire évoluer le statut<br/>(cycle de vie)"])
        UC_ARCH(["Archiver une initiative"])
        UC_ETAPE(["Gérer les étapes"])
        UC_JALON(["Gérer les jalons"])
        UC_USERS(["Gérer les utilisateurs"])
        UC_ROLES(["Gérer les rôles<br/>et permissions"])
    end

    Visiteur --> UC_AUTH
    Visiteur --> UC_CONSULT
    Visiteur --> UC_DETAIL
    Visiteur --> UC_DASH

    Responsable --> UC_CREER
    Responsable --> UC_MODIF
    Responsable --> UC_STATUT
    Responsable --> UC_ARCH
    Responsable --> UC_ETAPE
    Responsable --> UC_JALON

    Admin --> UC_USERS
    Admin --> UC_ROLES

    Responsable -.->|herite de| Visiteur
    Admin -.->|herite de| Responsable
```

---

## 2. Diagramme de classes (domaine)

```mermaid
classDiagram
    class Utilisateur {
        +Long idUser
        +String nom
        +String prenom
        +String email
        +String motDePasse
        +String telephone
        +Boolean actif
    }

    class Role {
        +Long idRole
        +String code
        +String libelle
    }

    class Permission {
        +Long idPermission
        +String code
        +String libelle
    }

    class RolePermission {
        +Long idRole
        +Long idPermission
    }

    class Entite {
        +Long idEntite
        +String code
        +String nom
        +Boolean actif
    }

    class Initiative {
        +Long idInitiative
        +String code
        +String nom
        +String description
        +LocalDate dateDebut
        +LocalDate dateFinPrevue
        +BigDecimal budget
        +Integer avancement
        +Boolean archive
    }

    class Etape {
        +Long idEtape
        +String nom
        +String description
        +Integer ordre
        +LocalDate dateDebut
        +LocalDate dateFinPrevue
        +Integer avancement
    }

    class Jalon {
        +Long idJalon
        +String nom
        +String description
        +LocalDate datePrevue
        +LocalDate dateRealisation
    }

    class StatutInitiative {
        +Long idStatut
        +String libelle
        +Integer ordre
    }

    class Priorite {
        +Long idPriorite
        +String libelle
        +Integer ordre
    }

    class StatutEtape {
        +Long idStatutEtape
        +String libelle
        +Integer ordre
    }

    class StatutJalon {
        +Long idStatutJalon
        +String libelle
        +Integer ordre
    }

    Role "1" --> "*" Utilisateur : attribue a
    Role "1" --> "*" RolePermission : possede
    Permission "1" --> "*" RolePermission : accordee a
    Entite "1" --> "*" Utilisateur : rattache
    Entite "1" --> "*" Initiative : porte

    Utilisateur "1" --> "*" Initiative : est responsable de
    StatutInitiative "1" --> "*" Initiative : etat de
    Priorite "1" --> "*" Initiative : niveau de

    Initiative "1" --> "*" Etape : composee de
    Initiative "1" --> "*" Jalon : jalonnee par
    StatutEtape "1" --> "*" Etape : etat de
    StatutJalon "1" --> "*" Jalon : etat de
```

---

## 3. Diagramme d'états — cycle de vie d'une initiative

Règles reflétées : création « À planifier » (ou directement « Terminée » si
avancement = 100 %, RM07), transitions autorisées uniquement, archivage réservé
aux statuts terminaux (RM14).

```mermaid
stateDiagram-v2
    [*] --> APLANIFIER : Créer
    [*] --> TERMINEE : Créer (avancement = 100 %)
    APLANIFIER --> ENCOURS : Démarrer
    APLANIFIER --> ANNULEE : Annuler
    ENCOURS --> ENPAUSE : Mettre en pause
    ENPAUSE --> ENCOURS : Reprendre
    ENCOURS --> TERMINEE : Avancement = 100 % (RM07)
    ENCOURS --> ANNULEE : Annuler
    ENPAUSE --> ANNULEE : Annuler
    TERMINEE --> [*] : Archiver (RM14)
    ANNULEE --> [*] : Archiver (RM14)
```

---

## 4. Diagramme de séquence — authentification JWT

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant SPA as Frontend React
    participant AC as AuthController
    participant AM as AuthenticationManager
    participant CU as CustomUserDetailsService
    participant DB as MySQL
    participant JWT as JwtService

    U->>SPA: Saisit email + mot de passe
    SPA->>AC: POST /api/auth/login
    AC->>AM: authenticate(email, motDePasse)
    AM->>CU: loadUserByUsername(email)
    CU->>DB: SELECT utilisateur + role + permissions
    DB-->>CU: Utilisateur (motDePasse Bcrypt)
    CU-->>AM: PrincipalUser (authorities)
    AM->>AM: BCrypt vérifie le mot de passe
    alt Identifiants valides
        AM-->>AC: Authentication
        AC->>JWT: generer(principal)
        JWT-->>AC: Jeton JWT (HS256, 8 h)
        AC-->>SPA: 200 LoginResponse (token + permissions)
        SPA-->>U: Redirection vers le dashboard
    else Identifiants invalides
        AM-->>AC: BadCredentialsException
        AC-->>SPA: 401 { message }
    end
```

---

## 5. Diagramme de séquence — création puis évolution d'une initiative

```mermaid
sequenceDiagram
    autonumber
    actor R as Responsable
    participant SPA as Frontend React
    participant IC as InitiativeController
    participant IS as InitiativeService
    participant DB as MySQL

    R->>SPA: Remplit le formulaire de création
    SPA->>IC: POST /api/initiatives (JWT)
    IC->>IS: creer(request)
    IS->>DB: existsByCode(code) (RM11)
    alt Code déjà utilisé
        IS-->>IC: BadRequestException
        IC-->>SPA: 400 (RM11)
    else Règles RM01/RM04/RM05/RM06 valides
        IS->>IS: statut = (avancement == 100) ? TERMINEE : APLANIFIER
        IS->>DB: INSERT initiative
        IS-->>IC: InitiativeResponse
        IC-->>SPA: 201 Created
    end

    R->>SPA: Modifie l'avancement / le statut
    SPA->>IC: PUT /api/initiatives/{id}
    IC->>IS: modifier(id, request)
    IS->>IS: verifierTransition(actuel, demande)
    alt Transition interdite
        IS-->>IC: BadRequestException
        IC-->>SPA: 400 (cycle de vie)
    else Transition autorisée
        IS->>IS: si avancement == 100 -> statut TERMINEE (RM07)
        IS->>DB: UPDATE initiative
        IS-->>IC: InitiativeResponse
        IC-->>SPA: 200 OK
    end

    R->>SPA: Archive l'initiative
    SPA->>IC: PATCH /api/initiatives/{id}/archive
    IC->>IS: archiver(id)
    IS->>IS: statut TERMINEE ou ANNULEE ? (RM14)
    IS->>DB: UPDATE archive = true
    IS-->>IC: InitiativeResponse
    IC-->>SPA: 200 OK
```

---

## 6. Diagramme de séquence — calcul des KPI du dashboard

Les KPI sont **calculés par l'API** (aucune donnée codée en dur).

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant SPA as Frontend React
    participant DC as DashboardController
    participant DS as DashboardService
    participant IR as InitiativeRepository
    participant DB as MySQL

    U->>SPA: Ouvre le dashboard
    SPA->>DC: GET /api/dashboard (JWT)
    DC->>DS: calculer()
    DS->>IR: countByArchiveFalse()
    DS->>IR: countByArchiveFalseAndStatutLibelle(...)
    DS->>IR: countEnRetard(today)
    DS->>IR: sumBudgetNonArchive()
    DS->>IR: avgAvancementNonArchive()
    DS->>IR: countByStatutNonArchive() / countByPrioriteNonArchive()
    IR->>DB: Requêtes d'agrégation SQL
    DB-->>IR: Résultats
    IR-->>DS: Agrégats
    DS->>DS: Arrondi avancement / budget
    DS-->>DC: DashboardResponse
    DC-->>SPA: 200 (KPI + graphes + liste en retard)
    SPA-->>U: Cartes KPI + graphiques (Chart.js)
```

---

## 7. Diagramme d'activité — création d'une initiative (règles métier)

```mermaid
flowchart TD
    A([Début]) --> B[/Saisie du formulaire/]
    B --> C{Nom renseigné ?<br/>RM01}
    C -- Non --> E1[400 : nom obligatoire]
    C -- Oui --> D{Code unique ?<br/>RM11}
    D -- Non --> E2[400 : code déjà utilisé]
    D -- Oui --> F{dateFin &gt;= dateDebut ?<br/>RM04}
    F -- Non --> E3[400 : dates incohérentes]
    F -- Oui --> G{budget &gt;= 0 ?<br/>RM05}
    G -- Non --> E4[400 : budget négatif]
    G -- Oui --> H{0 &lt;= avancement &lt;= 100 ?<br/>RM06}
    H -- Non --> E5[400 : avancement invalide]
    H -- Oui --> I{avancement = 100 ?<br/>RM07}
    I -- Oui --> J[Statut = Terminée]
    I -- Non --> K[Statut = À planifier]
    J --> L[(INSERT initiative)]
    K --> L
    L --> M([201 Created])
    E1 --> N([Fin])
    E2 --> N
    E3 --> N
    E4 --> N
    E5 --> N
    M --> N
```

---

## 8. Diagramme de composants — architecture logique

```mermaid
flowchart TB
    subgraph CLIENT["Poste client"]
        NAV["Navigateur<br/>(SPA React 18 / Vite)"]
        subgraph FRONT["Frontend (couche présentation)"]
            PAGES["Pages & composants<br/>React Router, Axios, Chart.js"]
            CTX["AuthContext<br/>(jeton JWT, permissions)"]
        end
    end

    subgraph SERVEUR["Serveur d'application (Spring Boot 3.5)"]
        SEC["Sécurité<br/>JwtAuthenticationFilter + SecurityConfig"]
        CTRL["Contrôleurs REST<br/>/api/**"]
        SVC["Services métier<br/>InitiativeService, DashboardService, ..."]
        MAP["Mappers & DTO"]
        REPO["Repositories JPA<br/>(Spring Data JPA)"]
        SPEC["Spécifications<br/>filtres combinés"]
    end

    subgraph DONNEES["Persistance"]
        MYSQL[("MySQL 8.4<br/>portefeuille_initiatives")]
        SCRIPTS["database/schema.sql<br/>database/data.sql"]
    end

    NAV --> PAGES
    PAGES --> CTX
    PAGES -->|"HTTPS / JSON"| CTRL
    CTRL --> SEC
    CTRL --> SVC
    SVC --> MAP
    SVC --> REPO
    REPO --> SPEC
    REPO -->|"JDBC"| MYSQL
    SCRIPTS -->|"spring.sql.init"| MYSQL
```

---

## 9. Diagramme de déploiement

```mermaid
flowchart TB
    subgraph DEV["Machine de développement"]
        subgraph BROWSER["Navigateur"]
            APP["Application React<br/>http://localhost:5173"]
        end
        subgraph JVM["JVM 17"]
            API["portefeuille-backend.jar<br/>Spring Boot / Tomcat<br/>http://localhost:8080"]
        end
        subgraph SGBD["Serveur MySQL 8.4"]
            DB[("Base portefeuille_initiatives<br/>localhost:3306")]
        end
    end

    APP -->|"REST /api/** (JSON, JWT Bearer)"| API
    API -->|"JDBC :3306"| DB
```

---

## 10. Modèle physique de données (ER)

```mermaid
erDiagram
    ROLE ||--o{ UTILISATEUR : attribue
    ROLE ||--o{ ROLE_PERMISSION : possede
    PERMISSION ||--o{ ROLE_PERMISSION : accordee
    ENTITE ||--o{ UTILISATEUR : rattache
    ENTITE ||--o{ INITIATIVE : porte
    UTILISATEUR ||--o{ INITIATIVE : responsable
    STATUT_INITIATIVE ||--o{ INITIATIVE : etat
    PRIORITE ||--o{ INITIATIVE : niveau
    INITIATIVE ||--o{ ETAPE : composee
    INITIATIVE ||--o{ JALON : jalonnee
    STATUT_ETAPE ||--o{ ETAPE : etat
    STATUT_JALON ||--o{ JALON : etat

    ROLE {
        bigint id_role PK
        varchar code UK
        varchar libelle
    }
    PERMISSION {
        bigint id_permission PK
        varchar code UK
        varchar libelle
    }
    ROLE_PERMISSION {
        bigint id_role PK,FK
        bigint id_permission PK,FK
    }
    ENTITE {
        bigint id_entite PK
        varchar code UK
        varchar nom
        boolean actif
    }
    UTILISATEUR {
        bigint id_user PK
        varchar nom
        varchar prenom
        varchar email UK
        varchar mot_de_passe
        varchar telephone
        boolean actif
        bigint id_role FK
        bigint id_entite FK
    }
    INITIATIVE {
        bigint id_initiative PK
        varchar code UK
        varchar nom
        text description
        date date_debut
        date date_fin_prevue
        decimal budget
        int avancement
        boolean archive
        bigint id_entite FK
        bigint id_responsable FK
        bigint id_statut FK
        bigint id_priorite FK
    }
    ETAPE {
        bigint id_etape PK
        varchar nom
        int ordre
        int avancement
        bigint id_initiative FK
        bigint id_statut_etape FK
    }
    JALON {
        bigint id_jalon PK
        varchar nom
        date date_prevue
        date date_realisation
        bigint id_initiative FK
        bigint id_statut_jalon FK
    }
```

---

## Références croisées

| Diagramme | Éléments du code source |
|---|---|
| Cas d'utilisation | `SecurityConfig`, permissions (données de référence) |
| Classes | `entity/*.java`, `Schema.sql` |
| États | `InitiativeService` (`verifierTransition`, RM07, RM14) |
| Séquence authentification | `AuthController`, `JwtService`, `CustomUserDetailsService` |
| Séquence création/cycle de vie | `InitiativeController`, `InitiativeService` |
| Séquence KPI | `DashboardController`, `DashboardService`, `InitiativeRepository` |
| Activité | `InitiativeRequest` (validations), `InitiativeService.validerReglesMetier` |
| Composants | `pom.xml`, `SecurityConfig`, `SpringDataJpa`, `styles.css` |
| Déploiement | `application.yml` (port 8080, datasource), `start-mysql.ps1` |
| ER | `database/schema.sql` |
