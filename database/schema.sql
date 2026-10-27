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
-- RISQUE (registre des risques d'une initiative) : description,
-- probabilité et impact notés de 1 à 3 (Faible / Moyen / Élevé),
-- responsable du plan d'action et plan d'action. La santé pondérée
-- = probabilité × impact (1 à 9) n'est JAMAIS stockée : elle est
-- recalculée à chaque lecture (score ≥ 6 => « critique », bande
-- rouge alignée sur CalculeurSante).
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risque (
    id_risque       BIGINT       NOT NULL AUTO_INCREMENT,
    description     TEXT         NOT NULL,
    probabilite     INT          NOT NULL,
    impact          INT          NOT NULL,
    plan_action     TEXT         NULL,
    id_initiative   BIGINT       NOT NULL,
    id_responsable  BIGINT       NULL,
    PRIMARY KEY (id_risque),
    CONSTRAINT fk_risque_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE CASCADE,
    CONSTRAINT fk_risque_responsable FOREIGN KEY (id_responsable) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT ck_risque_probabilite CHECK (probabilite BETWEEN 1 AND 3),
    CONSTRAINT ck_risque_impact      CHECK (impact      BETWEEN 1 AND 3)
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
CREATE TABLE IF NOT EXISTS historique_modification (
    id_historique    BIGINT        NOT NULL AUTO_INCREMENT,
    -- Type d'objet journalisé : initiative, etape, jalon, demande,
    -- utilisateur, role (valeur en clair, code i18n côté interface).
    type_objet       VARCHAR(30)   NOT NULL,
    -- Identifiant de l'objet dans son propre référentiel.
    id_objet         BIGINT        NOT NULL,
    -- Champ modifié ou événement (statut, budget, creation, suppression…).
    champ            VARCHAR(60)   NOT NULL,
    ancienne_valeur  VARCHAR(500)  NULL,
    nouvelle_valeur  VARCHAR(500)  NULL,
    commentaire      VARCHAR(500)  NULL,
    date_changement  DATETIME      NOT NULL,
    -- Origine du changement : FALSE = action manuelle d'un utilisateur,
    -- TRUE = moteur de règles (RM07-auto, réouverture, RM17, alignement
    -- au démarrage). Lecture seule depuis l'interface.
    automatique      BOOLEAN       NOT NULL DEFAULT FALSE,
    id_utilisateur   BIGINT        NULL,
    -- Initiative de rattachement (types initiative / etape / jalon
    -- uniquement) : lien de navigation pour le fil de la fiche, en
    -- ON DELETE CASCADE — l'objet parti, son fil part aussi. Les lignes
    -- demande / utilisateur / rôle restent NULL et survivent à toute
    -- suppression d'initiative.
    id_initiative    BIGINT        NULL,
    PRIMARY KEY (id_historique),
    CONSTRAINT fk_histmod_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_user) ON DELETE SET NULL,
    CONSTRAINT fk_histmod_initiative  FOREIGN KEY (id_initiative)  REFERENCES initiative (id_initiative) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- L'ordre chronologique est ce qui est affiché : l'index accélère la
-- lecture du fil d'une initiative (fiche détaillée).
-- MySQL 8.0 n'accepte pas « CREATE INDEX IF NOT EXISTS » (c'est une
-- extension MariaDB) : on garde donc l'index avec le même garde-fou
-- information_schema que les migrations de colonnes, plus bas.
SET @col = (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_modification'
        AND INDEX_NAME = 'idx_histmod_initiative');
SET @sql_index = IF(@col = 0,
        'CREATE INDEX idx_histmod_initiative ON historique_modification (id_initiative, date_changement)',
        'SELECT 1');
PREPARE stmt_index FROM @sql_index;
EXECUTE stmt_index;
DEALLOCATE PREPARE stmt_index;

-- La page « Historique » lit le journal ENTIER trié par date et filtre
-- sur la période : cet index sert le tri global et la vue d'archives.
SET @col = (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_modification'
        AND INDEX_NAME = 'idx_histmod_date');
SET @sql_date = IF(@col = 0,
        'CREATE INDEX idx_histmod_date ON historique_modification (date_changement)',
        'SELECT 1');
PREPARE stmt_date FROM @sql_date;
EXECUTE stmt_date;
DEALLOCATE PREPARE stmt_date;

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

-- Table : risque
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'risque' AND COLUMN_NAME = 'created_at');
SET @sql_audit = IF(@col = 0,
        'ALTER TABLE risque ADD COLUMN created_at DATETIME NULL, ADD COLUMN updated_at DATETIME NULL, ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT 1');
PREPARE stmt_audit FROM @sql_audit;
EXECUTE stmt_audit;
DEALLOCATE PREPARE stmt_audit;

-- Table : historique_statut (ANCIEN journal, à migrer) — origine du
-- changement (manuel / automatique). Colonnes garanties AVANT la copie
-- vers la table générique, pour les bases héritées qui ne l'ont pas
-- encore.
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_statut' AND COLUMN_NAME = 'automatique');
SET @sql_origine = IF(@col = 0,
        'ALTER TABLE historique_statut ADD COLUMN automatique BOOLEAN NOT NULL DEFAULT FALSE',
        'SELECT 1');
PREPARE stmt_origine FROM @sql_origine;
EXECUTE stmt_origine;
DEALLOCATE PREPARE stmt_origine;

-- ------------------------------------------------------------------
-- MIGRATION : l'ancien journal des statuts est absorbé par le journal
-- générique — une transition de statut devient une ligne
-- (type_objet = 'initiative', champ = 'statut', id_objet = id_initiative),
-- pertes en clair zéro (ancien / nouveau statut, commentaire, auteur,
-- date, origine). La colonne de rattachement reprend l'id de l'initiative.
--
-- Idempotent : la copie ne se déclenche que si l'ancienne table existe
-- ET que la nouvelle est vide (l'INSERT étant atomique, une exécution
-- interrompue après la copie ne rejoue jamais) ; DROP vient ensuite.
-- ------------------------------------------------------------------
SET @ancien_existe = (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'historique_statut');
SET @nouvelles_lignes = (SELECT COUNT(*) FROM historique_modification);
SET @sql_migration = IF(@ancien_existe = 1 AND @nouvelles_lignes = 0,
        'INSERT INTO historique_modification (type_objet, id_objet, champ, ancienne_valeur, nouvelle_valeur, commentaire, date_changement, automatique, id_utilisateur, id_initiative) SELECT ''initiative'', h.id_initiative, ''statut'', h.ancien_statut, h.nouveau_statut, h.commentaire, h.date_changement, h.automatique, h.id_utilisateur, h.id_initiative FROM historique_statut h',
        'SELECT 1');
PREPARE stmt_migration FROM @sql_migration;
EXECUTE stmt_migration;
DEALLOCATE PREPARE stmt_migration;

-- Rétroactif : les lignes déjà migrées dont le MOTIF déclare un passage
-- automatique sont reclassées « automatique » (ces motifs sont posés par le
-- code, jamais saisis librement). Idempotent : la garde `automatique = FALSE`
-- rend toute seconde exécution sans effet sur les lignes déjà classées.
UPDATE historique_modification
   SET automatique = TRUE
 WHERE automatique = FALSE
   AND (commentaire LIKE 'Passage automatique%'
     OR commentaire LIKE 'Retour automatique%'
     OR commentaire LIKE 'Réouverture automatique%'
     OR commentaire LIKE 'Initiative antérieure%');

-- L'ancienne table est désormais vide de sens : on la supprime pour qu'il
-- n'existe qu'UN seul journal, une seule preuve. (Gardée en dernier pour
-- que l'INSERT ci-dessus ait toujours son sourceur.)
SET @sql_drop = IF(@ancien_existe = 1,
        'DROP TABLE historique_statut',
        'SELECT 1');
PREPARE stmt_drop FROM @sql_drop;
EXECUTE stmt_drop;
DEALLOCATE PREPARE stmt_drop;

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