-- ============================================================
-- V8 : Invitations de collegues (Phase 2B4)
--
-- Une invitation lie une adresse e-mail a une organisation et a un
-- role, avec un jeton a usage unique et une expiration. L'acceptation
-- cree le compte dans l'organisation de l'invitateur.
-- ============================================================

CREATE TABLE invitation (
    id_invitation  BIGINT      NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    id_organisation BIGINT     NOT NULL,
    id_role        BIGINT      NOT NULL,
    jeton_hash     VARCHAR(64) NOT NULL,
    date_expiration DATETIME   NOT NULL,
    utilise        BOOLEAN     NOT NULL DEFAULT FALSE,
    cree_par       BIGINT      NOT NULL,
    cree_le        DATETIME    NOT NULL,
    PRIMARY KEY (id_invitation),
    CONSTRAINT uk_invitation_email UNIQUE (email),
    CONSTRAINT fk_invitation_organisation FOREIGN KEY (id_organisation) REFERENCES organisation (id_organisation),
    CONSTRAINT fk_invitation_role         FOREIGN KEY (id_role)         REFERENCES `role` (id_role),
    CONSTRAINT fk_invitation_cree_par     FOREIGN KEY (cree_par)        REFERENCES utilisateur (id_user)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_invitation_email ON invitation (email, date_expiration);
