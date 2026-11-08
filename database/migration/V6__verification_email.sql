-- ============================================================
-- V6 : Verification de l'adresse e-mail (Phase 2B - parcours client)
--
-- Ajoute email_verifie sur utilisateur (les comptes existants sont
-- reputes verifies) et la table jeton_verification (jetons à usage
-- unique, 24 heures) pour le circuit inscription -> verification ->
-- autorisation de se connecter.
-- ============================================================

-- Les comptes existants (crees avant cette fonctionnalite) sont verifies.
ALTER TABLE utilisateur ADD COLUMN email_verifie BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE utilisateur SET email_verifie = TRUE;
CREATE INDEX idx_utilisateur_email_verifie ON utilisateur (email_verifie);

-- Jeton de verification d'adresse e-mail (usage unique, 24 h).
-- L'email est la cle etrangere (unique sur utilisateur) : un email ne
-- peut appartenir qu'a un seul compte.
CREATE TABLE jeton_verification (
    id_jeton       BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    jeton_hash     VARCHAR(64)  NOT NULL,
    date_expiration DATETIME    NOT NULL,
    utilise        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id_jeton),
    CONSTRAINT uk_jeton_verification_email UNIQUE (email),
    CONSTRAINT fk_jeton_verification_utilisateur FOREIGN KEY (email) REFERENCES utilisateur (email) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_jeton_verification_email ON jeton_verification (email, date_expiration);
