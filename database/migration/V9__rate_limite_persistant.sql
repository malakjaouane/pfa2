-- ============================================================
-- V9 : Rate-limit persistant (Phase 1 C3)
--
-- Table des tentatives de connexion (anti force brute), persistée
-- pour survivre aux redémarrages et être partagée entre tous les
-- conteneurs backend. Deux compteurs glissants indépendants :
--  * par compte (e-mail) — max 5 échecs par fenêtre ;
--  * par adresse IP — max 20 échecs par fenêtre.
-- Une connexion réussie efface les tentatives du compte.
-- ============================================================

CREATE TABLE tentative_connexion (
    id_tentative   BIGINT       NOT NULL AUTO_INCREMENT,
    cle            VARCHAR(150) NOT NULL,   -- e-mail (compte) ou adresse IP
    type_cle       VARCHAR(10)  NOT NULL,   -- 'COMPTE' | 'IP'
    date_tentative DATETIME     NOT NULL,   -- horodatage de la tentative
    succes         BOOLEAN      NOT NULL,   -- TRUE si la connexion a reussi
    PRIMARY KEY (id_tentative),
    CONSTRAINT ck_tentative_type_cle CHECK (type_cle IN ('COMPTE', 'IP'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Compteurs glissants : lecture rapide par cle + type + date.
CREATE INDEX idx_tentative_cle_date ON tentative_connexion (type_cle, cle, date_tentative);
