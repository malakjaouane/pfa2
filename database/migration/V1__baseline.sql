-- ============================================================
-- Portefeuille d'initiatives digitales — IT6 Consulting
-- V1 : BASELINE — schéma physique (MPD) MySQL 8, état FINAL propre.
--
-- Ce fichier remplace l'ancien database/schema.sql (supprimé) :
--  * les blocs « ALTER protégé par information_schema » ont été aplatis
--    dans les CREATE TABLE (une base neuve n'a jamais rien à migrer) ;
--  * les colonnes d'audit (created_at / updated_at / version) sont
--    déclarées directement sur les tables concernées ;
--  * l'échelle des notes de risque est définitivement 1-5 ;
--  * l'ancien journal historique_statut et sa migration n'existent plus :
--    seul subsiste le journal générique historique_modification.
--
-- Une base existante créée avant Flyway n'EST PAS rechargée : Flyway y pose
-- un baseline (spring.flyway.baseline-on-migrate) et ne rejoue que les
-- migrations suivantes. Ce fichier ne s'exécute que sur une base vide.
--
-- Fidèle au cahier des charges (section 9.3 et Annexe A).
-- ============================================================

-- ------------------------------------------------------------------
-- RÔLE (la table est nommée « role » ; terme réservé MySQL 8 -> cité)
-- ------------------------------------------------------------------
CREATE TABLE `role` (
    id_role     BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    libelle     VARCHAR(100) NOT NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_role),
    CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- PERMISSION
-- ------------------------------------------------------------------
CREATE TABLE permission (
    id_permission BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(50)  NOT NULL,
    libelle       VARCHAR(100) NOT NULL,
    created_at    DATETIME     NULL,
    updated_at    DATETIME     NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_permission),
    CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- ROLE_PERMISSION  (association N : N entre Rôle et Permission)
-- ------------------------------------------------------------------
CREATE TABLE role_permission (
    id_role       BIGINT NOT NULL,
    id_permission BIGINT NOT NULL,
    PRIMARY KEY (id_role, id_permission),
    CONSTRAINT fk_rp_role       FOREIGN KEY (id_role)       REFERENCES `role` (id_role),
    CONSTRAINT fk_rp_permission FOREIGN KEY (id_permission) REFERENCES permission (id_permission)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- ENTIÉ
-- ------------------------------------------------------------------
CREATE TABLE entite (
    id_entite  BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50)  NOT NULL,
    nom        VARCHAR(100) NOT NULL,
    actif      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NULL,
    updated_at DATETIME     NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_entite),
    CONSTRAINT uk_entite_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- UTILISATEUR
-- ------------------------------------------------------------------
CREATE TABLE utilisateur (
    id_user      BIGINT       NOT NULL AUTO_INCREMENT,
    nom          VARCHAR(100) NOT NULL,
    prenom       VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    mot_de_passe VARCHAR(255) NOT NULL,
    telephone    VARCHAR(20)  NULL,
    actif        BOOLEAN      NOT NULL DEFAULT TRUE,
    id_role      BIGINT       NOT NULL,
    id_entite    BIGINT       NULL,
    created_at   DATETIME     NULL,
    updated_at   DATETIME     NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_user),
    CONSTRAINT uk_utilisateur_email UNIQUE (email),
    CONSTRAINT fk_utilisateur_role   FOREIGN KEY (id_role)   REFERENCES `role` (id_role),
    CONSTRAINT fk_utilisateur_entite FOREIGN KEY (id_entite) REFERENCES entite (id_entite)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- STATUT_INITIATIVE
-- ------------------------------------------------------------------
CREATE TABLE statut_initiative (
    id_statut BIGINT      NOT NULL AUTO_INCREMENT,
    libelle   VARCHAR(50) NOT NULL,
    ordre     INT         NOT NULL,
    PRIMARY KEY (id_statut),
    CONSTRAINT uk_statut_initiative_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- PRIORITE
-- ------------------------------------------------------------------
CREATE TABLE priorite (
    id_priorite BIGINT      NOT NULL AUTO_INCREMENT,
    libelle     VARCHAR(50) NOT NULL,
    ordre       INT         NOT NULL,
    PRIMARY KEY (id_priorite),
    CONSTRAINT uk_priorite_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- OBJECTIF STRATÉGIQUE (alignement Jira Align : initiative -> objectif)
-- ------------------------------------------------------------------
CREATE TABLE objectif_strategique (
    id_objectif BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    libelle     VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_objectif),
    CONSTRAINT uk_objectif_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- INITIATIVE
-- ------------------------------------------------------------------
CREATE TABLE initiative (
    id_initiative   BIGINT         NOT NULL AUTO_INCREMENT,
    code            VARCHAR(50)    NOT NULL,
    nom             VARCHAR(200)   NOT NULL,
    description     TEXT           NULL,
    date_debut      DATE           NULL,
    date_fin_prevue DATE           NULL,
    budget          DECIMAL(12, 2) NOT NULL DEFAULT 0,
    avancement      INT            NOT NULL DEFAULT 0,
    archive         BOOLEAN        NOT NULL DEFAULT FALSE,
    id_entite       BIGINT         NOT NULL,
    id_responsable  BIGINT         NULL,
    id_statut       BIGINT         NOT NULL,
    id_priorite     BIGINT         NOT NULL,
    id_objectif     BIGINT         NULL,
    created_at      DATETIME       NULL,
    updated_at      DATETIME       NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id_initiative),
    CONSTRAINT uk_initiative_code UNIQUE (code),
    CONSTRAINT fk_initiative_entite      FOREIGN KEY (id_entite)      REFERENCES entite (id_entite),
    CONSTRAINT fk_initiative_responsable FOREIGN KEY (id_responsable) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT fk_initiative_statut      FOREIGN KEY (id_statut)      REFERENCES statut_initiative (id_statut),
    CONSTRAINT fk_initiative_priorite    FOREIGN KEY (id_priorite)    REFERENCES priorite (id_priorite),
    CONSTRAINT fk_initiative_objectif    FOREIGN KEY (id_objectif)    REFERENCES objectif_strategique (id_objectif) ON DELETE SET NULL,
    -- RM05 : budget >= 0
    CONSTRAINT chk_initiative_budget CHECK (budget >= 0),
    -- RM06 : avancement entre 0 et 100
    CONSTRAINT chk_initiative_avancement CHECK (avancement BETWEEN 0 AND 100),
    -- RM04 : date de fin prévue >= date de début (si les deux sont renseignées)
    CONSTRAINT chk_initiative_dates CHECK (date_fin_prevue IS NULL OR date_debut IS NULL OR date_fin_prevue >= date_debut)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- STATUT_ETAPE
-- ------------------------------------------------------------------
CREATE TABLE statut_etape (
    id_statut_etape BIGINT      NOT NULL AUTO_INCREMENT,
    libelle         VARCHAR(50) NOT NULL,
    ordre           INT         NOT NULL,
    PRIMARY KEY (id_statut_etape),
    CONSTRAINT uk_statut_etape_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- ETAPE
-- ------------------------------------------------------------------
CREATE TABLE etape (
    id_etape        BIGINT       NOT NULL AUTO_INCREMENT,
    nom             VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    ordre           INT          NOT NULL,
    date_debut      DATE         NULL,
    date_fin_prevue DATE         NULL,
    avancement      INT          NOT NULL DEFAULT 0,
    id_initiative   BIGINT       NOT NULL,
    id_statut_etape BIGINT       NOT NULL,
    created_at      DATETIME     NULL,
    updated_at      DATETIME     NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_etape),
    CONSTRAINT fk_etape_initiative FOREIGN KEY (id_initiative)   REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_etape_statut     FOREIGN KEY (id_statut_etape) REFERENCES statut_etape (id_statut_etape),
    -- Avancement d'une étape entre 0 et 100
    CONSTRAINT chk_etape_avancement CHECK (avancement BETWEEN 0 AND 100),
    CONSTRAINT chk_etape_dates CHECK (date_fin_prevue IS NULL OR date_debut IS NULL OR date_fin_prevue >= date_debut)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- STATUT_JALON
-- ------------------------------------------------------------------
CREATE TABLE statut_jalon (
    id_statut_jalon BIGINT      NOT NULL AUTO_INCREMENT,
    libelle         VARCHAR(50) NOT NULL,
    ordre           INT         NOT NULL,
    PRIMARY KEY (id_statut_jalon),
    CONSTRAINT uk_statut_jalon_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- JALON
-- ------------------------------------------------------------------
CREATE TABLE jalon (
    id_jalon         BIGINT       NOT NULL AUTO_INCREMENT,
    nom              VARCHAR(200) NOT NULL,
    description      TEXT         NULL,
    date_prevue      DATE         NULL,
    date_realisation DATE         NULL,
    id_initiative    BIGINT       NOT NULL,
    id_statut_jalon  BIGINT       NOT NULL,
    created_at       DATETIME     NULL,
    updated_at       DATETIME     NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_jalon),
    CONSTRAINT fk_jalon_initiative FOREIGN KEY (id_initiative)   REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_jalon_statut     FOREIGN KEY (id_statut_jalon) REFERENCES statut_jalon (id_statut_jalon)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- RISQUE (registre des risques d'une initiative) : titre, description,
-- cycle de vie (ouverte -> attenuation -> close), échéance de
-- traitement, probabilité et impact notés de 1 à 5, responsable du
-- plan d'action et plan d'action. La santé pondérée = probabilité ×
-- impact (1 à 25) n'est JAMAIS stockée : elle est recalculée à chaque
-- lecture (bandes : < 5 verte, 5-9 ambre, 10-14 orange, >= 15 rouge,
-- alignées sur CalculeurSante).
-- ------------------------------------------------------------------
CREATE TABLE risque (
    id_risque      BIGINT       NOT NULL AUTO_INCREMENT,
    titre          VARCHAR(150) NOT NULL DEFAULT '',
    description    TEXT         NOT NULL,
    -- Cycle de vie du risque : ouverte (défaut) -> attenuation -> close.
    -- Code en clair, libellé traduit côté interface.
    statut         VARCHAR(20)  NOT NULL DEFAULT 'ouverte',
    -- Date cible de traitement (optionnelle) : pilote les alertes
    -- d'échéance dépassée (risque encore ouvert après cette date).
    echeance       DATE         NULL,
    -- Notes à l'échelle 1-5. Score = p × i (1-25).
    probabilite    INT          NOT NULL,
    impact         INT          NOT NULL,
    plan_action    TEXT         NULL,
    id_initiative  BIGINT       NOT NULL,
    id_responsable BIGINT       NULL,
    created_at     DATETIME     NULL,
    updated_at     DATETIME     NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_risque),
    CONSTRAINT fk_risque_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_risque_responsable FOREIGN KEY (id_responsable) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT ck_risque_probabilite CHECK (probabilite BETWEEN 1 AND 5),
    CONSTRAINT ck_risque_impact      CHECK (impact      BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- STATUT_DEMANDE (workflow ServiceNow SPM :
-- Brouillon -> Soumise -> En évaluation -> Acceptée / Refusée)
-- ------------------------------------------------------------------
CREATE TABLE statut_demande (
    id_statut_demande BIGINT      NOT NULL AUTO_INCREMENT,
    libelle           VARCHAR(50) NOT NULL,
    ordre             INT         NOT NULL,
    PRIMARY KEY (id_statut_demande),
    CONSTRAINT uk_statut_demande_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- DEMANDE_INITIATIVE : demande d'initiative digitale soumise au comité.
-- La transformation en initiative est enregistrée via id_initiative_liee ;
-- la contrainte UNIQUE garantit qu'une demande ne peut générer
-- qu'UNE seule initiative (aucun doublon).
-- ------------------------------------------------------------------
CREATE TABLE demande_initiative (
    id_demande           BIGINT       NOT NULL AUTO_INCREMENT,
    titre                VARCHAR(200) NOT NULL,
    description_besoin   TEXT         NULL,
    justification_metier TEXT         NULL,
    motif_refus          TEXT         NULL,
    date_soumission      DATE         NOT NULL,
    id_entite            BIGINT       NOT NULL,
    id_demandeur         BIGINT       NOT NULL,
    id_priorite          BIGINT       NOT NULL,
    id_statut_demande    BIGINT       NOT NULL,
    id_initiative_liee   BIGINT       NULL,
    archive              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           DATETIME     NULL,
    updated_at           DATETIME     NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_demande),
    CONSTRAINT fk_demande_entite     FOREIGN KEY (id_entite)          REFERENCES entite (id_entite),
    CONSTRAINT fk_demande_demandeur  FOREIGN KEY (id_demandeur)       REFERENCES utilisateur (id_user),
    CONSTRAINT fk_demande_priorite   FOREIGN KEY (id_priorite)        REFERENCES priorite (id_priorite),
    CONSTRAINT fk_demande_statut     FOREIGN KEY (id_statut_demande)  REFERENCES statut_demande (id_statut_demande),
    CONSTRAINT fk_demande_initiative FOREIGN KEY (id_initiative_liee) REFERENCES initiative (id_initiative) ON DELETE SET NULL,
    CONSTRAINT uq_demande_initiative UNIQUE (id_initiative_liee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- HISTORIQUE DES MODIFICATIONS : journal générique d'audit du
-- portefeuille. Une ligne = un champ d'un objet qui a changé (ancienne
-- valeur, nouvelle, auteur, date, origine manuelle/automatique). Le
-- même journal absorbe les transitions de statut des initiatives
-- (champ « statut »), la vie des étapes et jalons, le workflow des
-- demandes, et l'administration (comptes, rôles, permissions).
--
-- JOURNAL IMMUABLE : aucune écriture n'est exposée à l'interface et
-- aucune suppression n'existe (plus de ligne, plus de purge). Les
-- lignes de plus de 12 mois quittent la vue active par un calcul sur
-- la date côté service — jamais de destruction.
--
-- Les valeurs sont stockées en clair et non par clé étrangère :
-- le journal doit survivre à une modification des référentiels.
-- ------------------------------------------------------------------
CREATE TABLE historique_modification (
    id_historique   BIGINT        NOT NULL AUTO_INCREMENT,
    -- Type d'objet journalisé : initiative, etape, jalon, demande,
    -- utilisateur, role (valeur en clair, code i18n côté interface).
    type_objet      VARCHAR(30)   NOT NULL,
    -- Identifiant de l'objet dans son propre référentiel.
    id_objet        BIGINT        NOT NULL,
    -- Champ modifié ou événement (statut, budget, creation, suppression…).
    champ           VARCHAR(60)   NOT NULL,
    ancienne_valeur VARCHAR(500)  NULL,
    nouvelle_valeur VARCHAR(500)  NULL,
    commentaire     VARCHAR(500)  NULL,
    date_changement DATETIME      NOT NULL,
    -- Origine du changement : FALSE = action manuelle d'un utilisateur,
    -- TRUE = moteur de règles (RM07-auto, réouverture, RM17, alignement
    -- au démarrage). Lecture seule depuis l'interface.
    automatique     BOOLEAN       NOT NULL DEFAULT FALSE,
    id_utilisateur  BIGINT        NULL,
    -- Initiative de rattachement (types initiative / etape / jalon
    -- uniquement) : lien de navigation pour le fil de la fiche, en
    -- ON DELETE CASCADE — l'objet parti, son fil part aussi. Les lignes
    -- demande / utilisateur / rôle restent NULL et survivent à toute
    -- suppression d'initiative.
    id_initiative   BIGINT        NULL,
    PRIMARY KEY (id_historique),
    CONSTRAINT fk_histmod_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT fk_histmod_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- Journal des requêtes IA (assistant « Demande à ton portefeuille »).
-- Traçabilité complète exigée par la loi 09-08 / CNDP : chaque question
-- ou résumé généré est enregistré avec son auteur, son statut (OK,
-- ERREUR, NON_DISPONIBLE), sa durée, et le cas échéant l'initiative
-- concernée.
-- ------------------------------------------------------------------
CREATE TABLE journal_ia (
    id_ia          BIGINT        NOT NULL AUTO_INCREMENT,
    id_utilisateur BIGINT        NULL,
    type_requete   VARCHAR(20)   NOT NULL,
    demande        VARCHAR(2000) NOT NULL,
    reponse        TEXT          NULL,
    id_initiative  BIGINT        NULL,
    modele         VARCHAR(50)   NULL,
    statut         VARCHAR(20)   NOT NULL,
    message_erreur VARCHAR(300)  NULL,
    duree_ms       BIGINT        NULL,
    date_requete   DATETIME      NOT NULL,
    PRIMARY KEY (id_ia),
    CONSTRAINT fk_journalia_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT fk_journalia_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- Historique des connexions (traçabilité des tentatives de connexion,
-- loi 09-08) : chaque tentative est horodatée, réussie ou non, avec
-- l'adresse IP et le motif d'échec. L'e-mail saisi est conservé tel quel
-- (une tentative sur un compte inconnu reste traçable) ; le compte
-- rattaché passe en NULL s'il est supprimé entre-temps.
-- ------------------------------------------------------------------
CREATE TABLE historique_connexion (
    id_connexion   BIGINT       NOT NULL AUTO_INCREMENT,
    date_connexion DATETIME     NOT NULL,
    email          VARCHAR(150) NOT NULL,
    id_utilisateur BIGINT       NULL,
    adresse_ip     VARCHAR(45)  NOT NULL,
    succes         BOOLEAN      NOT NULL,
    motif          VARCHAR(200) NULL,
    PRIMARY KEY (id_connexion),
    CONSTRAINT fk_connexion_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- COMMENTAIRES d'initiative : fil de discussion autour du dossier.
-- L'auteur est anonymisé (SET NULL) si son compte part — le fil reste.
-- ------------------------------------------------------------------
CREATE TABLE commentaire (
    id_commentaire BIGINT       NOT NULL AUTO_INCREMENT,
    id_initiative  BIGINT       NOT NULL,
    id_auteur      BIGINT       NULL,
    contenu        TEXT         NOT NULL,
    created_at     DATETIME     NULL,
    updated_at     DATETIME     NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id_commentaire),
    CONSTRAINT fk_commentaire_initiative FOREIGN KEY (id_initiative) REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_commentaire_auteur     FOREIGN KEY (id_auteur)     REFERENCES utilisateur (id_user)     ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- DEPENSES rattachées à une initiative : suivi financier déclaratif
-- (libellé, montant, date, catégorie). Le montant est positif par
-- contrainte ; l'auteur de la saisie est anonymisé en cas de départ.
-- NB : ces dépenses sont DÉCLARATIVES — le portefeuille n'est pas un
-- outil comptable, aucun écriture comptable n'est propagée.
-- ------------------------------------------------------------------
CREATE TABLE depense (
    id_depense     BIGINT        NOT NULL AUTO_INCREMENT,
    id_initiative  BIGINT        NOT NULL,
    libelle        VARCHAR(150)  NOT NULL,
    montant        DECIMAL(15,2) NOT NULL,
    date_depense   DATE          NOT NULL,
    categorie      VARCHAR(50)   NULL,
    id_saisisseur  BIGINT        NULL,
    created_at     DATETIME      NULL,
    updated_at     DATETIME      NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id_depense),
    CONSTRAINT fk_depense_initiative FOREIGN KEY (id_initiative) REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_depense_saisisseur FOREIGN KEY (id_saisisseur) REFERENCES utilisateur (id_user)     ON DELETE SET NULL,
    CONSTRAINT ck_depense_montant    CHECK (montant > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- JOURNAL D'AUDIT (actions HTTP) : traces d'écriture posées par le
-- filtre servlet — QUI a fait QUOI (verbe + route) depuis OÙ (IP),
-- quel que soit le résultat (statut HTTP). Complémentaire du journal
-- des modifications (QUOI a changé champ à champ) : l'un prouve
-- l'action, l'autre prouve l'effet.
--
-- Interdits par conception, vérifiés par le filtre :
--   * aucun champ de mot de passe dans le corps enregistré (masqué),
--   * aucune ligne pour GET/HEAD/OPTIONS (lecture non intrusive),
--   * aucune ligne pour PUT /api/initiatives/{id} : ce journal est
--     déjà renseigné champ à champ par InitiativeService.
-- Le corps n'est conservé que pour les routes API (commence par /api/)
-- et tronqué à 300 caractères.
-- ------------------------------------------------------------------
CREATE TABLE journal_audit (
    id_audit       BIGINT       NOT NULL AUTO_INCREMENT,
    date_action    DATETIME     NOT NULL,
    id_utilisateur BIGINT       NULL,
    email          VARCHAR(150) NULL,
    methode        VARCHAR(10)  NOT NULL,
    route          VARCHAR(300) NOT NULL,
    statut_http    INT          NOT NULL,
    adresse_ip     VARCHAR(45)  NOT NULL,
    corps          VARCHAR(300) NULL,
    PRIMARY KEY (id_audit),
    CONSTRAINT fk_audit_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ==================================================================
-- INDEX (MySQL 8 n'accepte pas « CREATE INDEX IF NOT EXISTS » : ici,
-- chaque migration Flyway ne s'exécute qu'UNE fois, l'ajout direct
-- est donc possible.)
-- ==================================================================

-- L'ordre chronologique est ce qui est affiché : fil d'une initiative.
CREATE INDEX idx_histmod_initiative ON historique_modification (id_initiative, date_changement);

-- Page « Historique » : tri global et vue d'archives.
CREATE INDEX idx_histmod_date ON historique_modification (date_changement);

-- Lecture administrative du journal IA (100 dernières, tri date).
CREATE INDEX idx_journal_ia_date ON journal_ia (date_requete);

-- Lecture administrative des connexions (tri date décroissante).
CREATE INDEX idx_connexion_date ON historique_connexion (date_connexion);

-- Fil de la fiche : commentaire par initiative, du plus récent au plus ancien.
CREATE INDEX idx_commentaire_initiative ON commentaire (id_initiative, created_at);

-- Filtres de la page dépenses : par initiative et par date.
CREATE INDEX idx_depense_initiative ON depense (id_initiative, date_depense);

-- Lecture administrative de l'audit (tri date décroissante).
CREATE INDEX idx_audit_date ON journal_audit (date_action);
