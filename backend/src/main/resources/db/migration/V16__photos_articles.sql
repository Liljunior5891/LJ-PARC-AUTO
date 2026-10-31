-- =============================================================================
-- V16 : photo des articles et icone des categories du point de vente.
--
-- La photo est une image deja reduite par le navigateur (quelques dizaines de
-- Ko), conservee en data URL comme le logo de la societe : la carte d'un
-- restaurant s'affiche sans service de fichiers supplementaire.
-- =============================================================================

ALTER TABLE pdv_article ADD COLUMN image TEXT;
ALTER TABLE pdv_categorie ADD COLUMN icone VARCHAR(16);
