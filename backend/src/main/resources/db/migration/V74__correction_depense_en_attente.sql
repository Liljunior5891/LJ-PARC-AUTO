-- Correction d'une depense deja validee, proposee par le gestionnaire : elle
-- attend l'administrateur sans toucher a la depense ni a ses reglements.
-- Approuvee, elle s'applique ; refusee, la depense reste telle qu'elle etait.
ALTER TABLE depense ADD COLUMN correction_en_attente TEXT;
