-- ============================================================
-- V11 : Plans d'abonnement (Phase 2D)
--
-- Catalogue des plans (gratuit / pro) avec leurs limites.
-- L'organisation porte son code de plan (colonne plan existante) ;
-- ce tableau de référence permet de vérifier les limites côté serveur.
-- ============================================================

CREATE TABLE plan (
    id_plan             BIGINT      NOT NULL AUTO_INCREMENT,
    code                VARCHAR(30) NOT NULL,
    nom                 VARCHAR(100) NOT NULL,
    nb_utilisateurs_max INT         NOT NULL,
    nb_initiatives_max  INT         NOT NULL,
    ia_incluse          BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id_plan),
    CONSTRAINT uk_plan_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO plan (code, nom, nb_utilisateurs_max, nb_initiatives_max, ia_incluse) VALUES
('gratuit', 'Gratuit', 3, 3, FALSE),
('pro', 'Pro', 25, 100, TRUE);
