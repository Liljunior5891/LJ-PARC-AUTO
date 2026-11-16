-- Cle de la vente cote appareil : la meme part avec l'encaissement en ligne et, si la
-- reponse se perd, avec sa copie hors ligne. Le serveur reconnait ainsi un renvoi et
-- n'enregistre jamais deux fois la meme vente.
ALTER TABLE pdv_vente ADD COLUMN cle_hors_ligne VARCHAR(64);
CREATE UNIQUE INDEX ux_pdv_vente_cle_hors_ligne ON pdv_vente (entreprise_id, cle_hors_ligne)
    WHERE cle_hors_ligne IS NOT NULL;
