-- Phase 4C : priorisation des initiatives (score valeur/effort)
ALTER TABLE initiative
    ADD COLUMN valeur INT NULL COMMENT 'Valeur metier de l initiative (1-5)',
    ADD COLUMN effort INT NULL COMMENT 'Effort estime de l initiative (1-5)',
    ADD COLUMN score_priorisation INT NULL COMMENT 'Score de priorisation calcule (valeur * 25 / effort)';

-- Contraintes de domaine : valeur et effort comprises entre 1 et 5
ALTER TABLE initiative
    ADD CONSTRAINT chk_valeur CHECK (valeur IS NULL OR (valeur >= 1 AND valeur <= 5)),
    ADD CONSTRAINT chk_effort CHECK (effort IS NULL OR (effort >= 1 AND effort <= 5));

-- Index pour le tri par score de priorisation
CREATE INDEX idx_initiative_score_priorisation ON initiative(score_priorisation);
