-- =============================================================================
-- V29 : photo du vehicule.
--
-- La photo est televersee depuis la fiche du vehicule, reduite dans le
-- navigateur et enregistree comme une image encodee (comme les photos des
-- articles du point de vente) : la colonne passe en texte. Elle remplace le
-- dessin du type sur la carte du vehicule dans la vue du parc.
-- =============================================================================

ALTER TABLE camion ALTER COLUMN photo_url TYPE TEXT;
