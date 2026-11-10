-- ============================================================
-- V12 : repare les comptes provisionnes non verifies.
--
-- Bug : V6 marque comme verifies les comptes EXISTANTS au moment de
-- son execution. Sur une base vierge, V6 ne trouve aucune ligne, puis
-- DataInitializer cree admin@it6.ma (+ comptes de demonstration en dev)
-- avec le defaut email_verifie = FALSE : le login est alors bloque par
-- 403 EMAIL_NON_VERIFIE sur toute nouvelle installation depuis la Phase 2B.
--
-- Ces comptes sont provisionnes (ADMIN_PASSWORD / seed de developpement),
-- pas issus d'une inscription : la verification d'e-mail ne s'applique
-- pas a eux. Reparation idempotente pour les bases creees entre V6 et ce
-- correctif ; DataInitializer positionne email_verifie = TRUE des la
-- creation pour les bases suivantes.
-- ============================================================
UPDATE utilisateur
SET email_verifie = TRUE
WHERE email_verifie = FALSE
  AND email IN ('admin@it6.ma', 'responsable@it6.ma', 'visiteur@it6.ma');
