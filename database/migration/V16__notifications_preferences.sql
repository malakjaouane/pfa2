-- Phase 4D : préférences de notification par utilisateur
CREATE TABLE preference_notification (
    id_preference_notification BIGINT NOT NULL AUTO_INCREMENT,
    id_utilisateur BIGINT NOT NULL,
    email_actif BOOLEAN NOT NULL DEFAULT FALSE,
    frequence VARCHAR(20) NOT NULL DEFAULT 'aucune', -- 'aucune', 'quotidienne', 'hebdomadaire'
    whatsapp_actif BOOLEAN NOT NULL DEFAULT FALSE,
    telephone VARCHAR(30),
    date_creation DATETIME NOT NULL,
    date_modification DATETIME,
    PRIMARY KEY (id_preference_notification),
    CONSTRAINT fk_preference_notification_utilisateur FOREIGN KEY (id_utilisateur)
        REFERENCES utilisateur(id_user),
    CONSTRAINT uk_preference_notification_utilisateur UNIQUE (id_utilisateur),
    CONSTRAINT chk_frequence CHECK (frequence IN ('aucune', 'quotidienne', 'hebdomadaire')),
    INDEX idx_preference_notification_utilisateur (id_utilisateur)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Table des notifications envoyées (historique, immuable)
CREATE TABLE notification (
    id_notification BIGINT NOT NULL AUTO_INCREMENT,
    id_utilisateur BIGINT NOT NULL,
    canal VARCHAR(20) NOT NULL, -- 'email', 'whatsapp'
    sujet VARCHAR(255),
    corps TEXT NOT NULL,
    date_envoi DATETIME NOT NULL,
    statut VARCHAR(20) NOT NULL, -- 'envoyee', 'echec'
    PRIMARY KEY (id_notification),
    CONSTRAINT fk_notification_utilisateur FOREIGN KEY (id_utilisateur)
        REFERENCES utilisateur(id_user),
    INDEX idx_notification_utilisateur (id_utilisateur),
    INDEX idx_notification_date (date_envoi)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
