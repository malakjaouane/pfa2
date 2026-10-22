-- ============================================================
-- Portefeuille d'initiatives digitales — IT6 Consulting
-- DONNÉES DE RÉFÉRENCE (référentiels + profils de droits)
-- Les mots de passe des utilisateurs sont créés par l'application
-- (hash BCrypt) via le DataInitializer ; aucun mot de passe en clair
-- ni en base ni dans ce fichier.
-- ============================================================

-- ------------------------------------------------------------------
-- RÔLES (3 profils du cahier des charges)
-- ------------------------------------------------------------------
INSERT IGNORE INTO `role` (id_role, code, libelle) VALUES
    (1, 'ADMIN',        'Administrateur'),
    (2, 'RESPONSABLE',  'Responsable'),
    (3, 'VISITEUR',     'Visiteur');

-- ------------------------------------------------------------------
-- PERMISSIONS
-- ------------------------------------------------------------------
INSERT IGNORE INTO permission (id_permission, code, libelle) VALUES
    (1,  'INITIATIVE_CONSULTER',   'Consulter les initiatives'),
    (2,  'INITIATIVE_CREER',       'Créer une initiative'),
    (3,  'INITIATIVE_MODIFIER',    'Modifier une initiative'),
    (4,  'INITIATIVE_ARCHIVER',    'Archiver une initiative'),
    (5,  'RESPONSABLE_AFFECTER',   'Affecter un responsable à une initiative'),
    (6,  'AVANCEMENT_MODIFIER',    'Mettre à jour l''avancement'),
    (7,  'ETAPE_GERER',            'Gérer les étapes d''une initiative'),
    (8,  'JALON_GERER',            'Gérer les jalons d''une initiative'),
    (9,  'DASHBOARD_CONSULTER',    'Consulter le dashboard et les KPI'),
    (10, 'FILTRE_UTILISER',        'Utiliser la recherche et les filtres'),
    (11, 'UTILISATEUR_GERER',      'Gérer les utilisateurs'),
    (12, 'ROLE_GERER',             'Gérer les rôles'),
    (13, 'PERMISSION_GERER',       'Gérer les permissions'),
    (14, 'DEMANDE_SOUMETTRE',      'Soumettre une demande d''initiative'),
    (15, 'DEMANDE_EVALUER',        'Évaluer, accepter ou refuser une demande'),
    (16, 'BUDGET_CONSULTER',       'Consulter les budgets et les écarts'),
    (17, 'IA_UTILISER',            'Utiliser l''assistant IA du portefeuille');

-- ------------------------------------------------------------------
-- RÔLE <-> PERMISSION
-- ------------------------------------------------------------------
-- ADMINISTRATEUR : toutes les permissions (1 à 17)
INSERT IGNORE INTO role_permission (id_role, id_permission) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (1, 13), (1, 14), (1, 15), (1, 16), (1, 17);

-- RESPONSABLE : gestion des initiatives, étapes, jalons, dashboard, filtres,
-- budgets + soumission et évaluation des demandes (workflow SPM)
-- + assistant IA (17)
INSERT IGNORE INTO role_permission (id_role, id_permission) VALUES
    (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10), (2, 14), (2, 15), (2, 16), (2, 17);

-- VISITEUR : consultation, dashboard, filtres (pas d'assistant IA)
INSERT IGNORE INTO role_permission (id_role, id_permission) VALUES
    (3, 1), (3, 9), (3, 10);

-- ------------------------------------------------------------------
-- ENTITÉS (unités organisationnelles de rattachement)
-- ------------------------------------------------------------------
INSERT IGNORE INTO entite (id_entite, code, nom, actif) VALUES
    (1, 'IT6', 'IT6 Consulting',     TRUE),
    (2, 'DV',  'Direction Digitale', TRUE),
    (3, 'SI',  'Systèmes d''Information', TRUE);

-- ------------------------------------------------------------------
-- STATUTS D'INITIATIVE (statuts métier — l'archivage N'EST PAS un statut)
-- ------------------------------------------------------------------
INSERT IGNORE INTO statut_initiative (id_statut, libelle, ordre) VALUES
    (1, 'À planifier', 1),
    (2, 'En cours',    2),
    (3, 'En pause',    3),
    (4, 'Terminée',    4),
    (5, 'Annulée',     5);

-- ------------------------------------------------------------------
-- PRIORITÉS
-- ------------------------------------------------------------------
INSERT IGNORE INTO priorite (id_priorite, libelle, ordre) VALUES
    (1, 'Basse',    1),
    (2, 'Moyenne',  2),
    (3, 'Haute',    3),
    (4, 'Critique', 4);

-- ------------------------------------------------------------------
-- STATUTS D'ÉTAPE
-- ------------------------------------------------------------------
INSERT IGNORE INTO statut_etape (id_statut_etape, libelle, ordre) VALUES
    (1, 'À faire',  1),
    (2, 'En cours', 2),
    (3, 'Terminée', 3);

-- ------------------------------------------------------------------
-- STATUTS DE JALON
-- ------------------------------------------------------------------
INSERT IGNORE INTO statut_jalon (id_statut_jalon, libelle, ordre) VALUES
    (1, 'À atteindre', 1),
    (2, 'Atteint',     2);

-- ------------------------------------------------------------------
-- OBJECTIFS STRATÉGIQUES (alignement initiative -> objectif)
-- ------------------------------------------------------------------
INSERT IGNORE INTO objectif_strategique (id_objectif, code, libelle, description) VALUES
    (1, 'OBJ-EXP',  'Améliorer l''expérience client digitale', 'Digitaliser les parcours clients et les services en ligne.'),
    (2, 'OBJ-SI',   'Moderniser le système d''information',    'Moderniser les outils internes : intranet, portail RH, applicatifs.'),
    (3, 'OBJ-CLOUD','Accélérer la transformation cloud',      'Migrer les infrastructures et études cloud.');

-- Les rattachements initiative -> objectif sont posés par `DataInitializer`,
-- au moment même de la création des initiatives : un UPDATE ici s'exécuterait
-- avant qu'aucune initiative n'existe, donc il resterait sans effet jusqu'au
-- démarrage suivant. On laisse donc ce fichier se limiter aux objectifs eux-
-- mêmes, qui sont des référentiels et non des données liées.

-- ------------------------------------------------------------------
-- STATUTS DE DEMANDE (workflow : Brouillon -> Soumise ->
-- En évaluation -> Acceptée / Refusée)
-- ------------------------------------------------------------------
INSERT IGNORE INTO statut_demande (id_statut_demande, libelle, ordre) VALUES
    (1, 'Brouillon',     1),
    (2, 'Soumise',       2),
    (3, 'En évaluation', 3),
    (4, 'Acceptée',      4),
    (5, 'Refusée',       5);