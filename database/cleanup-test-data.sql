-- ============================================================
-- Nettoyage des données laissées par la suite de tests API
-- (database/tests-api.ps1).
-- ------------------------------------------------------------
-- Supprime uniquement les artefacts de test :
--   * initiatives de test, archivées ou non (code préfixé TEST) ;
--   * initiatives issues de la transformation de demandes de test
--     (code DEM-xxxxxx, demandes au titre préfixé TEST-DEM) ;
--   * demandes d'initiatives de test ;
--   * objectifs stratégiques de test (code préfixé TEST-OBJ) ;
--   * comptes de test désactivés (email test*@it6.ma).
-- Les données de démonstration et les données utilisateur réelles
-- ne sont jamais touchées.
-- Usage :
--   mysql -u pfa_user -p portefeuille_initiatives < database/cleanup-test-data.sql
-- ============================================================

-- Encodage de la connexion : sans cela, le client mysql interprète les
-- motifs accentués du fichier (« Résumé… ») en latin1 et ils ne matchent
-- plus rien en base (utf8mb4).
SET NAMES utf8mb4;

-- Les étapes, jalons et risques liés sont supprimés en cascade
-- (ON DELETE CASCADE) : le registre des risques suit son initiative.
--
-- On ne requiert PAS archive = 1 : une exécution interrompue (ex. refus
-- d'archivage en fin de parcours) laisse une initiative de test non archivée,
-- et le nettoyage doit pouvoir la retirer aussi. Le préfixe TEST est réservé
-- à la suite de tests — les codes de démonstration (DIG-CLI-001…) commencent
-- tous autrement.
DELETE FROM initiative
WHERE code LIKE 'TEST%';

-- Initiatives issues de la transformation de demandes de test (avant la
-- suppression des demandes elles-mêmes pour conserver la liaison).
DELETE ini FROM initiative ini
JOIN demande_initiative d ON d.id_initiative_liee = ini.id_initiative
WHERE d.titre LIKE 'TEST-DEM%';

-- Journal des modifications (audit immuable) : les lignes qui traçaient
-- des demandes de test partent AVANT les demandes elles-mêmes — passé ce
-- point, l'id_objet ne référencer plus rien et la ligne serait orpheline.
-- Les lignes de démonstration restent : leur demande existe toujours.
DELETE FROM historique_modification
WHERE type_objet = 'demande'
  AND id_objet IN (SELECT id_demande FROM demande_initiative WHERE titre LIKE 'TEST-DEM%');

DELETE FROM demande_initiative
WHERE titre LIKE 'TEST-DEM%';

DELETE FROM objectif_strategique
WHERE code LIKE 'TEST-OBJ%';

-- Requêtes IA de la suite de tests (journal de l'assistant) : la question
-- envoyée est préfixée TEST, et un résumé ne peut référencer qu'une
-- initiative TEST (déjà supprimée ci-dessus → id_initiative = NULL).
DELETE FROM journal_ia
WHERE demande LIKE 'TEST%'
   OR demande LIKE 'Résumé de l''initiative TEST%';

-- Tentatives de connexion des comptes de test (historique des
-- connexions) : l'e-mail saisi est la trace, qu'un compte soit rattaché
-- ou non (une tentative échouée sur un compte désactivé reste tracée).
DELETE FROM historique_connexion
WHERE email LIKE 'test%@it6.ma';

-- Journal des modifications écrit PAR des comptes de test (objet de
-- démonstration ou autre) : à retirer AVANT les comptes, sinon l'auteur
-- passe en NULL (ON DELETE SET NULL) et la ligne devient introuvable.
DELETE FROM historique_modification
WHERE id_utilisateur IN (SELECT id_user FROM utilisateur WHERE email LIKE 'test%@it6.ma');

-- Journal des modifications ÉCRIT SUR des comptes de test (création,
-- modification, suppression du compte par l'administrateur de la suite) :
-- id_objet = compte de test, auteur = admin — même impératif d'ordre.
DELETE FROM historique_modification
WHERE type_objet = 'utilisateur'
  AND id_objet IN (SELECT id_user FROM utilisateur WHERE email LIKE 'test%@it6.ma');

DELETE FROM utilisateur
WHERE email LIKE 'test%@it6.ma';

-- Filet de sécurité : toute ligne de journal encore orpheline d'une
-- exécution antérieure (demande ou compte déjà disparu). Les objets de
-- démonstration existent toujours — leurs lignes ne sont pas touchées.
DELETE FROM historique_modification
WHERE (type_objet = 'demande'
       AND id_objet NOT IN (SELECT id_demande FROM demande_initiative))
   OR (type_objet = 'utilisateur'
       AND id_objet NOT IN (SELECT id_user FROM utilisateur));

-- Aller-retours de permissions sur les rôles pendant la suite (une
-- permission ajoutée au Visiteur puis remise en état) : la démonstration
-- ne touche jamais aux rôles, elle n'a donc AUCUNE ligne de ce type —
-- toutes celles présentes sont résiduelles.
DELETE FROM historique_modification
WHERE type_objet = 'role';
