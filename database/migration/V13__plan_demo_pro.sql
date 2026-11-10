-- ============================================================
-- V13 : l'organisation de demonstration passe en plan « pro »
--
-- Le seed (DataInitializer) deploie 4 initiatives pour l'organisation
-- « it6 » alors que le plan gratuit n'autorise que 3 : toute creation
-- d'initiative etait refusee (400 LIMITE_PLAN) dans chaque environnement
-- de demonstration (dev + conteneur partagent la meme base).
--
-- DataInitializer cree desormais l'organisation en 'pro' (voir commentaire
-- dans le code) ; cette migration repare idempotement les bases deja
-- seedees. Elle ne touche QUE l'organisation de demonstration encore en
-- 'gratuit' qui depasse deja la limite : un client reel en 'gratuit'
-- ne peut pas atteindre ce compteur par l'API (la limite l'en empeche),
-- donc aucune organisation tierce n'est reclassifiee.
-- ============================================================

UPDATE organisation
SET plan = 'pro'
WHERE slug = 'it6'
  AND plan = 'gratuit'
  AND (SELECT COUNT(*)
       FROM initiative
       WHERE initiative.id_organisation = organisation.id_organisation) > 3;
