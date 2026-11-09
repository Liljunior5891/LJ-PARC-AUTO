-- Nouveau droit : chaque agence a son tableau de bord, vu par son superviseur et ses caissiers.
-- Accorde par defaut aux deux profils dans les entreprises dont les droits sont deja regles.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'TABLEAU_BORD' FROM agc_reglage
ON CONFLICT DO NOTHING;

INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'CAISSIER_AGENCE', 'TABLEAU_BORD' FROM agc_reglage
ON CONFLICT DO NOTHING;
