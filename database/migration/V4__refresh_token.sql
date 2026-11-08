-- ============================================================
-- V4 : jetons de rafraîchissement (refresh token) à rotation.
--
-- Access token court (15 min) servi en cookie HttpOnly + Secure +
-- SameSite=Strict (cf. CookieService) ; à son expiration le client
-- présente le cookie de refresh, qui est alors TOURNÉ (marqué utilisé,
-- un nouveau est émis). Toute réutilisation d'un jeton déjà tourné
-- révoque tous les jetons du compte (détection de vol).
--
-- Seul le HACHAGE SHA-256 du jeton est stocké : une fuite de la base
-- ne donne aucun jeton utilisable.
--
-- Révocations :
--   * déconnexion (logout) ;
--   * changement de mot de passe (UtilisateurService).
-- ============================================================
CREATE TABLE refresh_token (
    id_refresh       BIGINT      NOT NULL AUTO_INCREMENT,
    -- Hachage SHA-256 (hex, 64 caractères) du jeton opaque.
    jeton_hash       VARCHAR(64) NOT NULL,
    id_user          BIGINT      NOT NULL,
    date_creation    DATETIME    NOT NULL,
    date_expiration  DATETIME    NOT NULL,
    -- TRUE dès la première présentation : une seconde présentation est
    -- une réutilisation (vol potentiel) -> révocation du compte.
    utilise          BOOLEAN     NOT NULL DEFAULT FALSE,
    date_utilisation DATETIME    NULL,
    PRIMARY KEY (id_refresh),
    CONSTRAINT uk_refresh_token_hash UNIQUE (jeton_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (id_user)
        REFERENCES utilisateur (id_user) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Lecture par compte (révocation) et purge des jetons expirés.
CREATE INDEX idx_refresh_user ON refresh_token (id_user, date_expiration);
