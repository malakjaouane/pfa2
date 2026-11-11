-- Phase 4 (correctif) : les tables créées par V14 (piece_jointe) et V16
-- (notification, preference_notification) avaient omis les colonnes de la
-- base commune portée par les entités (BaseAuditable / BaseOrganisation).
-- Sans elles, Hibernate refuse de démarrer (ddl-auto : validate).
--
-- Conventions V1 / V5 / V14 :
--  * created_at / updated_at NULLables — on n'invente pas de date de
--    création pour les enregistrements préexistants (cf. BaseAuditable) ;
--  * version NOT NULL DEFAULT 0 — verrou optimiste JPA ;
--  * id_organisation rétro-rempli depuis l'utilisateur destinataire,
--    puis rendu NOT NULL + clé étrangère (isolation multi-locataire).

-- 1. notification (V16) -----------------------------------------------
ALTER TABLE notification
    ADD COLUMN id_organisation BIGINT NULL,
    ADD COLUMN created_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE notification n
    JOIN utilisateur u ON u.id_user = n.id_utilisateur
   SET n.id_organisation = u.id_organisation;

ALTER TABLE notification
    MODIFY COLUMN id_organisation BIGINT NOT NULL,
    ADD CONSTRAINT fk_notification_organisation
        FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation),
    ADD INDEX idx_notification_organisation (id_organisation);

-- 2. preference_notification (V16) ------------------------------------
ALTER TABLE preference_notification
    ADD COLUMN id_organisation BIGINT NULL,
    ADD COLUMN created_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE preference_notification p
    JOIN utilisateur u ON u.id_user = p.id_utilisateur
   SET p.id_organisation = u.id_organisation;

ALTER TABLE preference_notification
    MODIFY COLUMN id_organisation BIGINT NOT NULL,
    ADD CONSTRAINT fk_preference_notification_organisation
        FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation),
    ADD INDEX idx_preference_notification_organisation (id_organisation);

-- 3. piece_jointe (V14 avait id_organisation mais pas la tracabilité) --
ALTER TABLE piece_jointe
    ADD COLUMN created_at DATETIME NULL,
    ADD COLUMN updated_at DATETIME NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
