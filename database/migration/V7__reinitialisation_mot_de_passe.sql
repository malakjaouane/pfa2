-- ============================================================
-- V7 : Mot de passe oublie (Phase 2B3)
--
-- Jeton a usage unique (1 heure) pour la reinitialisation du mot de
-- passe. Seul le HACHAGE SHA-256 est stocke.
-- ============================================================

CREATE TABLE jeton_reinitialisation (
    id_jeton       BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    jeton_hash     VARCHAR(64)  NOT NULL,
    date_expiration DATETIME    NOT NULL,
    utilise        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id_jeton),
    CONSTRAINT uk_jeton_reinitialisation_email UNIQUE (email),
    CONSTRAINT fk_jeton_reinitialisation_utilisateur FOREIGN KEY (email) REFERENCES utilisateur (email) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_jeton_reinitialisation_email ON jeton_reinitialisation (email, date_expiration);
