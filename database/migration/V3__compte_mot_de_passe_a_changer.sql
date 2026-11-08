-- ============================================================
-- V3 : marqueur « changement de mot de passe obligatoire ».
--
-- Le compte administrateur créé lors d'une installation hors profil
-- dev reçoit doit_changer_mot_de_passe = TRUE : le premier login
-- n'autorise alors que POST /api/auth/changer-mot-de-passe (filtre
-- FiltreMotDePasseAChanger), et le marqueur retombe à FALSE une fois
-- le nouveau mot de passe enregistré.
--
-- Par défaut FALSE : les comptes existants (dont la démonstration)
-- ne sont pas impactés.
-- ============================================================
ALTER TABLE utilisateur
    ADD COLUMN doit_changer_mot_de_passe BOOLEAN NOT NULL DEFAULT FALSE;
