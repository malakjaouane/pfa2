-- Phase 4A : pièces jointes sur initiatives et commentaires
-- Table de stockage des fichiers uploadés, isolée par organisation.
CREATE TABLE piece_jointe (
    id_piece_jointe BIGINT NOT NULL AUTO_INCREMENT,
    nom_fichier VARCHAR(255) NOT NULL,
    type_mime VARCHAR(100) NOT NULL,
    taille_octets BIGINT NOT NULL,
    cle_stockage VARCHAR(500) NOT NULL,
    id_organisation BIGINT NOT NULL,
    id_initiative BIGINT,
    id_commentaire BIGINT,
    id_utilisateur BIGINT NOT NULL,
    date_creation DATETIME NOT NULL,
    PRIMARY KEY (id_piece_jointe),
    CONSTRAINT fk_piece_jointe_organisation FOREIGN KEY (id_organisation)
        REFERENCES organisation(id_organisation),
    CONSTRAINT fk_piece_jointe_initiative FOREIGN KEY (id_initiative)
        REFERENCES initiative(id_initiative),
    CONSTRAINT fk_piece_jointe_commentaire FOREIGN KEY (id_commentaire)
        REFERENCES commentaire(id_commentaire),
    CONSTRAINT fk_piece_jointe_utilisateur FOREIGN KEY (id_utilisateur)
        REFERENCES utilisateur(id_user),
    CONSTRAINT chk_piece_jointe_cible CHECK (
        (id_initiative IS NOT NULL AND id_commentaire IS NULL)
        OR (id_initiative IS NULL AND id_commentaire IS NOT NULL)
    ),
    INDEX idx_piece_jointe_organisation (id_organisation),
    INDEX idx_piece_jointe_initiative (id_initiative),
    INDEX idx_piece_jointe_commentaire (id_commentaire)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
