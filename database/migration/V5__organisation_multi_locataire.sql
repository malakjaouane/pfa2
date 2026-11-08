-- ============================================================
-- V5 : Multi-organisation (SaaS, Phase 2A)
--
-- Nouvelle entite Organisation et colonne id_organisation
-- (NOT NULL, FK, indexee) sur toutes les tables metier afin
-- d'isoler les donnees par organisation. Toutes les donnees
-- existantes (demo IT6) sont rattachees a l'organisation
-- "IT6 Consulting" (slug it6, id = 1).
--
-- Le filtre Hibernate "filtreParOrganisation" (condition
-- id_organisation = :idOrganisation) est active sur chaque
-- requete authentifiee via OrganisationIsolationInterceptor :
-- une requete de l'organisation A ne voit jamais les donnees
-- de l'organisation B.
-- ============================================================

CREATE TABLE organisation (
    id_organisation BIGINT NOT NULL AUTO_INCREMENT,
    nom VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    plan VARCHAR(40) NOT NULL DEFAULT 'gratuit',
    statut VARCHAR(40) NOT NULL DEFAULT 'active',
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_organisation),
    CONSTRAINT uk_organisation_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Organisation par defaut : IT6 Consulting (id = 1).
INSERT INTO organisation (nom, slug, plan, statut, created_at, updated_at, version)
VALUES ('IT6 Consulting', 'it6', 'gratuit', 'active', NOW(), NOW(), 0);

-- ------------------------------------------------------------------
-- entite
-- ------------------------------------------------------------------
ALTER TABLE entite ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE entite SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE entite ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE entite ADD CONSTRAINT fk_entite_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_entite_organisation ON entite (id_organisation);

-- ------------------------------------------------------------------
-- utilisateur
-- ------------------------------------------------------------------
ALTER TABLE utilisateur ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE utilisateur SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE utilisateur ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE utilisateur ADD CONSTRAINT fk_utilisateur_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_utilisateur_organisation ON utilisateur (id_organisation);

-- ------------------------------------------------------------------
-- objectif_strategique
-- ------------------------------------------------------------------
ALTER TABLE objectif_strategique ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE objectif_strategique SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE objectif_strategique ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE objectif_strategique ADD CONSTRAINT fk_objectif_strategique_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_objectif_strategique_organisation ON objectif_strategique (id_organisation);

-- ------------------------------------------------------------------
-- initiative
-- ------------------------------------------------------------------
ALTER TABLE initiative ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE initiative SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE initiative ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE initiative ADD CONSTRAINT fk_initiative_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_initiative_organisation ON initiative (id_organisation);

-- ------------------------------------------------------------------
-- demande_initiative
-- ------------------------------------------------------------------
ALTER TABLE demande_initiative ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE demande_initiative SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE demande_initiative ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE demande_initiative ADD CONSTRAINT fk_demande_initiative_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_demande_initiative_organisation ON demande_initiative (id_organisation);

-- ------------------------------------------------------------------
-- risque
-- ------------------------------------------------------------------
ALTER TABLE risque ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE risque SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE risque ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE risque ADD CONSTRAINT fk_risque_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_risque_organisation ON risque (id_organisation);

-- ------------------------------------------------------------------
-- depense
-- ------------------------------------------------------------------
ALTER TABLE depense ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE depense SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE depense ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE depense ADD CONSTRAINT fk_depense_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_depense_organisation ON depense (id_organisation);

-- ------------------------------------------------------------------
-- commentaire
-- ------------------------------------------------------------------
ALTER TABLE commentaire ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE commentaire SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE commentaire ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE commentaire ADD CONSTRAINT fk_commentaire_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_commentaire_organisation ON commentaire (id_organisation);

-- ------------------------------------------------------------------
-- historique_modification (journal des modifications)
-- ------------------------------------------------------------------
ALTER TABLE historique_modification ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE historique_modification SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE historique_modification ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE historique_modification ADD CONSTRAINT fk_historique_modification_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_historique_modification_organisation ON historique_modification (id_organisation);

-- ------------------------------------------------------------------
-- journal_audit (journal des requêtes HTTP)
-- ------------------------------------------------------------------
ALTER TABLE journal_audit ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE journal_audit SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE journal_audit ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE journal_audit ADD CONSTRAINT fk_journal_audit_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_journal_audit_organisation ON journal_audit (id_organisation);

-- ------------------------------------------------------------------
-- journal_ia (journal des requêtes IA)
-- ------------------------------------------------------------------
ALTER TABLE journal_ia ADD COLUMN id_organisation BIGINT NOT NULL DEFAULT 1;
UPDATE journal_ia SET id_organisation = (SELECT id_organisation FROM organisation WHERE slug = 'it6');
ALTER TABLE journal_ia ALTER COLUMN id_organisation DROP DEFAULT;
ALTER TABLE journal_ia ADD CONSTRAINT fk_journal_ia_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation);
CREATE INDEX idx_journal_ia_organisation ON journal_ia (id_organisation);
