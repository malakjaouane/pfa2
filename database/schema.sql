-- ============================================================
-- Portefeuille d'initiatives digitales — IT6 Consulting
-- SCHÉMA PHYSIQUE (MPD) — MySQL 8
-- Fidèle au cahier des charges (section 9.3 et Annexe A)
-- ============================================================

-- ------------------------------------------------------------------
-- RÔLE (la table est nommée « role » ; terme réservé MySQL 8 -> cité)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
    id_role   BIGINT       NOT NULL AUTO_INCREMENT,
    code      VARCHAR(50)  NOT NULL,
    libelle   VARCHAR(100) NOT NULL,
    PRIMARY KEY (id_role),
    CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- PERMISSION
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS permission (
    id_permission BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(50)  NOT NULL,
    libelle       VARCHAR(100) NOT NULL,
    PRIMARY KEY (id_permission),
    CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- ROLE_PERMISSION  (association N : N entre Rôle et Permission)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role_permission (
    id_role       BIGINT NOT NULL,
    id_permission BIGINT NOT NULL,
    PRIMARY KEY (id_role, id_permission),
    CONSTRAINT fk_rp_role       FOREIGN KEY (id_role)       REFERENCES `role` (id_role),
    CONSTRAINT fk_rp_permission FOREIGN KEY (id_permission) REFERENCES permission (id_permission)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- ENTIÉ
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS entite (
    id_entite BIGINT       NOT NULL AUTO_INCREMENT,
    code      VARCHAR(50)  NOT NULL,
    nom       VARCHAR(100) NOT NULL,
    actif     BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id_entite),
    CONSTRAINT uk_entite_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- UTILISATEUR
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS utilisateur (
    id_user       BIGINT       NOT NULL AUTO_INCREMENT,
    nom           VARCHAR(100) NOT NULL,
    prenom        VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    mot_de_passe  VARCHAR(255) NOT NULL,
    telephone     VARCHAR(20)  NULL,
    actif         BOOLEAN      NOT NULL DEFAULT TRUE,
    id_role       BIGINT       NOT NULL,
    id_entite     BIGINT       NULL,
    PRIMARY KEY (id_user),
    CONSTRAINT uk_utilisateur_email UNIQUE (email),
    CONSTRAINT fk_utilisateur_role   FOREIGN KEY (id_role)   REFERENCES `role` (id_role),
    CONSTRAINT fk_utilisateur_entite FOREIGN KEY (id_entite) REFERENCES entite (id_entite)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- STATUT_INITIATIVE
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS statut_initiative (
    id_statut BIGINT      NOT NULL AUTO_INCREMENT,
    libelle   VARCHAR(50) NOT NULL,
    ordre     INT         NOT NULL,
    PRIMARY KEY (id_statut),
    CONSTRAINT uk_statut_initiative_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- PRIORITE
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS priorite (
    id_priorite BIGINT      NOT NULL AUTO_INCREMENT,
    libelle     VARCHAR(50) NOT NULL,
    ordre       INT         NOT NULL,
    PRIMARY KEY (id_priorite),
    CONSTRAINT uk_priorite_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- OBJECTIF STRATÉGIQUE (alignement Jira Align : initiative -> objectif)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS objectif_strategique (
    id_objectif BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    libelle     VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    PRIMARY KEY (id_objectif),
    CONSTRAINT uk_objectif_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- INITIATIVE
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS initiative (
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

-- Migration douce d'une base antérieure : MySQL ne connaît pas
-- « ADD COLUMN IF NOT EXISTS », on protège donc l'ALTER par un contrôle
-- préalable sur information_schema (idempotent : no-op si la colonne,
-- puis la clé étrangère, existent déjà).
SET @col_obj = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initiative' AND COLUMN_NAME = 'id_objectif');
SET @sql_obj = IF(@col_obj = 0,
        'ALTER TABLE initiative ADD COLUMN id_objectif BIGINT NULL',
        'SELECT 1');
PREPARE stmt_obj FROM @sql_obj;
EXECUTE stmt_obj;
DEALLOCATE PREPARE stmt_obj;

SET @fk_obj = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'initiative' AND CONSTRAINT_NAME = 'fk_initiative_objectif');
SET @sql_fk = IF(@fk_obj = 0,
        'ALTER TABLE initiative ADD CONSTRAINT fk_initiative_objectif FOREIGN KEY (id_objectif) REFERENCES objectif_strategique (id_objectif) ON DELETE SET NULL',
        'SELECT 1');
PREPARE stmt_fk FROM @sql_fk;
EXECUTE stmt_fk;
DEALLOCATE PREPARE stmt_fk;

-- ------------------------------------------------------------------
-- STATUT_ETAPE
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS statut_etape (
    id_statut_etape BIGINT      NOT NULL AUTO_INCREMENT,
    libelle         VARCHAR(50) NOT NULL,
    ordre           INT         NOT NULL,
    PRIMARY KEY (id_statut_etape),
    CONSTRAINT uk_statut_etape_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- ETAPE
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS etape (
    id_etape         BIGINT       NOT NULL AUTO_INCREMENT,
    nom              VARCHAR(200) NOT NULL,
    description      TEXT         NULL,
    ordre            INT          NOT NULL,
    date_debut       DATE         NULL,
    date_fin_prevue  DATE         NULL,
    avancement       INT          NOT NULL DEFAULT 0,
    id_initiative    BIGINT       NOT NULL,
    id_statut_etape  BIGINT       NOT NULL,
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
CREATE TABLE IF NOT EXISTS statut_jalon (
    id_statut_jalon BIGINT      NOT NULL AUTO_INCREMENT,
    libelle         VARCHAR(50) NOT NULL,
    ordre           INT         NOT NULL,
    PRIMARY KEY (id_statut_jalon),
    CONSTRAINT uk_statut_jalon_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- JALON
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS jalon (
    id_jalon         BIGINT       NOT NULL AUTO_INCREMENT,
    nom              VARCHAR(200) NOT NULL,
    description      TEXT         NULL,
    date_prevue      DATE         NULL,
    date_realisation DATE         NULL,
    id_initiative    BIGINT       NOT NULL,
    id_statut_jalon  BIGINT       NOT NULL,
    PRIMARY KEY (id_jalon),
    CONSTRAINT fk_jalon_initiative FOREIGN KEY (id_initiative)   REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_jalon_statut     FOREIGN KEY (id_statut_jalon) REFERENCES statut_jalon (id_statut_jalon)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- STATUT_DEMANDE (workflow ServiceNow SPM :
-- Brouillon -> Soumise -> En évaluation -> Acceptée / Refusée)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS statut_demande (
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
CREATE TABLE IF NOT EXISTS demande_initiative (
    id_demande          BIGINT       NOT NULL AUTO_INCREMENT,
    titre               VARCHAR(200) NOT NULL,
    description_besoin  TEXT         NULL,
    justification_metier TEXT        NULL,
    motif_refus         TEXT         NULL,
    date_soumission     DATE         NOT NULL,
    id_entite           BIGINT       NOT NULL,
    id_demandeur        BIGINT       NOT NULL,
    id_priorite         BIGINT       NOT NULL,
    id_statut_demande   BIGINT       NOT NULL,
    id_initiative_liee  BIGINT       NULL,
    archive             BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id_demande),
    CONSTRAINT fk_demande_entite     FOREIGN KEY (id_entite)          REFERENCES entite (id_entite),
    CONSTRAINT fk_demande_demandeur  FOREIGN KEY (id_demandeur)       REFERENCES utilisateur (id_user),
    CONSTRAINT fk_demande_priorite   FOREIGN KEY (id_priorite)        REFERENCES priorite (id_priorite),
    CONSTRAINT fk_demande_statut     FOREIGN KEY (id_statut_demande)  REFERENCES statut_demande (id_statut_demande),
    CONSTRAINT fk_demande_initiative FOREIGN KEY (id_initiative_liee) REFERENCES initiative (id_initiative) ON DELETE SET NULL,
    CONSTRAINT uq_demande_initiative UNIQUE (id_initiative_liee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Migration douce : colonne « archive » de la bascule Actives | Archives —
-- même garde que pour id_objectif (MySQL ignore « ADD COLUMN IF NOT
-- EXISTS ») : no-op si la colonne existe déjà sur une base antérieure.
SET @col_arch_dem = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande_initiative' AND COLUMN_NAME = 'archive');
SET @sql_arch_dem = IF(@col_arch_dem = 0,
        'ALTER TABLE demande_initiative ADD COLUMN archive BOOLEAN NOT NULL DEFAULT FALSE',
        'SELECT 1');
PREPARE stmt_arch_dem FROM @sql_arch_dem;
EXECUTE stmt_arch_dem;
DEALLOCATE PREPARE stmt_arch_dem;

-- ------------------------------------------------------------------
-- HISTORIQUE DES STATUTS : journal chronologique des transitions de
-- cycle de vie d'une initiative (RM10 : un comblement du planning
-- interdit toute modification). Permet d'afficher « qui, quand, et
-- pourquoi » sur la fiche détaillée.
--
-- Les libellés sont stockés en clair et non par clé étrangère : le
-- journal doit survivre à une modification du référentiel de statuts.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS historique_statut (
    id_historique   BIGINT        NOT NULL AUTO_INCREMENT,
    id_initiative   BIGINT        NOT NULL,
    ancien_statut   VARCHAR(50)   NULL,
    nouveau_statut  VARCHAR(50)   NOT NULL,
    commentaire     VARCHAR(500)  NULL,
    date_changement DATETIME      NOT NULL,
    -- Origine du changement : FALSE = action manuelle d'un utilisateur,
    -- TRUE = moteur de règles (RM07-auto, réouverture, RM17, alignement au
    -- démarrage). Lecture seule depuis l'interface (page « Historique des
    -- statuts ») : aucune écriture utilisateur n'est exposée.
    automatique     BOOLEAN       NOT NULL DEFAULT FALSE,
    id_utilisateur  BIGINT        NULL,
    PRIMARY KEY (id_historique),
    CONSTRAINT fk_historique_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_historique_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- L'ordre chronologique est ce qui est affiché : l'index accélère la lecture
-- du journal d'une initiative.
-- MySQL 8.0 n'accepte pas « CREATE INDEX IF NOT EXISTS » (c'est une
-- extension MariaDB) : on garde donc l'index avec le même garde-fou
-- information_schema que les migrations de colonnes, plus bas.
SET @col = (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_statut'
        AND INDEX_NAME = 'idx_historique_statut_initiative');
SET @sql_index = IF(@col = 0,
        'CREATE INDEX idx_historique_statut_initiative ON historique_statut (id_initiative, date_changement)',
        'SELECT 1');
PREPARE stmt_index FROM @sql_index;
EXECUTE stmt_index;
DEALLOCATE PREPARE stmt_index;

-- ==================================================================
-- MIGRATIONS DOUCES
-- MySQL ne connaît pas « ADD COLUMN IF NOT EXISTS » : chaque ALTER est
-- donc protégé par un contrôle sur information_schema, exactement comme
-- pour initiative.id_objectif ci-dessus. Le résultat est idempotent —
-- ces blocs sont des no-op sur une base déjà à jour.
--
-- Colonnes d'audit de la table concernée :
--   created_at : date de création (jamais mise à jour)
--   updated_at : date de dernière modification
--   version    : verrou optimiste (@Version JPA) — deux utilisateurs
--                modifiant la même ligne ne se marchent pas dessus :
--                le second reçoit un HTTP 409.
--
-- created_at / updated_at restent NULLables : ces colonnes sont ajoutées
-- rétroactivement, on refuse donc d'inventer une date de création pour
-- les enregistrements préexistants. version vaut 0 par défaut.
-- ==================================================================

-- Table : role (nom réservé MySQL 8 : les backticks ne servent que dans le DDL,
-- jamais dans le comparatif information_schema, où le nom est déja échappé).
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'role' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE `role` ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : permission
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'permission' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE permission ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : entite
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entite' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE entite ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : utilisateur
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'utilisateur' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE utilisateur ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : objectif_strategique
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'objectif_strategique' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE objectif_strategique ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : initiative
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'initiative' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE initiative ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : etape
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'etape' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE etape ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : jalon
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jalon' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE jalon ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : demande_initiative
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demande_initiative' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE demande_initiative ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : historique_statut — origine du changement (manuel / automatique).
-- Colonne de la page « Historique des statuts » : FALSE = action explicite
-- d'un utilisateur, TRUE = moteur de règles. Lecture seule côté interface.
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_statut' AND COLUMN_NAME = 'automatique');
SET @sql_origine = IF(@col = 0,
        'ALTER TABLE historique_statut ADD COLUMN automatique BOOLEAN NOT NULL DEFAULT FALSE',
        'SELECT 1');
PREPARE stmt_origine FROM @sql_origine;
EXECUTE stmt_origine;
DEALLOCATE PREPARE stmt_origine;

-- Rétroactif : les lignes déjà au journal dont le MOTIF déclare un passage
-- automatique sont reclassées « automatique » (ces motifs sont posés par le
-- code, jamais saisis librement). Idempotent : la garde `automatique = FALSE`
-- rend toute seconde exécution sans effet sur les lignes déjà classées.
UPDATE historique_statut
   SET automatique = TRUE
 WHERE automatique = FALSE
   AND (commentaire LIKE 'Passage automatique%'
     OR commentaire LIKE 'Retour automatique%'
     OR commentaire LIKE 'Réouverture automatique%'
     OR commentaire LIKE 'Initiative antérieure%');

-- La page « Historique des statuts » lit le journal ENTIER trié par date et
-- filtre sur la période : contrairement à l'index (initiative, date) qui sert
-- la fiche, cet index sert le tri global.
SET @col = (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_statut'
        AND INDEX_NAME = 'idx_historique_statut_date');
SET @sql_date = IF(@col = 0,
        'CREATE INDEX idx_historique_statut_date ON historique_statut (date_changement)',
        'SELECT 1');
PREPARE stmt_date FROM @sql_date;
EXECUTE stmt_date;
DEALLOCATE PREPARE stmt_date;

-- ------------------------------------------------------------------
-- Journal des requêtes IA (assistant « Demande à ton portefeuille »).
-- Traçabilité complète exigée par la loi 09-08 / CNDP : chaque question
-- ou résumé généré est enregistré avec son auteur, son statut (OK,
-- ERREUR, NON_DISPONIBLE), sa durée, et le cas échéant l'initiative
-- concernée. Table ajoutée en fin de fichier : les deux clés étrangères
-- (utilisateur, initiative) sont alors garanties créées.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS journal_ia (
    id_ia           BIGINT        NOT NULL AUTO_INCREMENT,
    id_utilisateur  BIGINT        NULL,
    type_requete    VARCHAR(20)   NOT NULL,
    demande         VARCHAR(2000) NOT NULL,
    reponse         TEXT          NULL,
    id_initiative   BIGINT        NULL,
    modele          VARCHAR(50)   NULL,
    statut          VARCHAR(20)   NOT NULL,
    message_erreur  VARCHAR(300)  NULL,
    duree_ms        BIGINT        NULL,
    date_requete    DATETIME      NOT NULL,
    PRIMARY KEY (id_ia),
    CONSTRAINT fk_journalia_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT fk_journalia_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Lecture administrative du journal (les 100 dernières, tri date).
SET @col = (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'journal_ia'
        AND INDEX_NAME = 'idx_journal_ia_date');
SET @sql_date_ia = IF(@col = 0,
        'CREATE INDEX idx_journal_ia_date ON journal_ia (date_requete)',
        'SELECT 1');
PREPARE stmt_date_ia FROM @sql_date_ia;
EXECUTE stmt_date_ia;
DEALLOCATE PREPARE stmt_date_ia;

-- ------------------------------------------------------------------
-- Historique des connexions (traçabilité des tentatives de connexion,
-- loi 09-08) : chaque tentative est horodatée, réussie ou non, avec
-- l'adresse IP et le motif d'échec. L'e-mail saisi est conservé tel quel
-- (une tentative sur un compte inconnu reste traçable) ; le compte
-- rattaché passe en NULL s'il est supprimé entre-temps.
-- Table ajoutée en fin de fichier : la clé étrangère vers utilisateur
-- est alors garantie créée.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS historique_connexion (
    id_connexion    BIGINT        NOT NULL AUTO_INCREMENT,
    date_connexion  DATETIME      NOT NULL,
    email           VARCHAR(150)  NOT NULL,
    id_utilisateur  BIGINT        NULL,
    adresse_ip      VARCHAR(45)   NOT NULL,
    succes          BOOLEAN       NOT NULL,
    motif           VARCHAR(200)  NULL,
    PRIMARY KEY (id_connexion),
    CONSTRAINT fk_connexion_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Lecture administrative (tri date décroissante, filtre e-mail / IP).
SET @col = (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_connexion'
        AND INDEX_NAME = 'idx_connexion_date');
SET @sql_conn_date = IF(@col = 0,
        'CREATE INDEX idx_connexion_date ON historique_connexion (date_connexion)',
        'SELECT 1');
PREPARE stmt_conn_date FROM @sql_conn_date;
EXECUTE stmt_conn_date;
DEALLOCATE PREPARE stmt_conn_date;