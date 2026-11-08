-- ============================================================
-- V10 : Droits par initiative (Phase 2C)
--
-- Table membre_initiative : un utilisateur peut être membre d'une
-- initiative avec un rôle (responsable, contributeur, lecteur).
-- Un contributeur ne modifie que ses initiatives (contrôle côté serveur).
-- ============================================================

CREATE TABLE membre_initiative (
    id_membre      BIGINT      NOT NULL AUTO_INCREMENT,
    id_user        BIGINT      NOT NULL,
    id_initiative  BIGINT      NOT NULL,
    role           VARCHAR(30) NOT NULL, -- responsable, contributeur, lecteur
    PRIMARY KEY (id_membre),
    CONSTRAINT uk_membre UNIQUE (id_user, id_initiative),
    CONSTRAINT fk_membre_user FOREIGN KEY (id_user) REFERENCES utilisateur (id_user) ON DELETE CASCADE,
    CONSTRAINT fk_membre_initiative FOREIGN KEY (id_initiative) REFERENCES initiative (id_initiative) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_membre_user ON membre_initiative (id_user);
CREATE INDEX idx_membre_initiative ON membre_initiative (id_initiative);
