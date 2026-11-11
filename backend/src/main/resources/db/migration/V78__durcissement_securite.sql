-- Corrections de l'audit securite du 2026-11-11.

-- Plateforme : une seule declaration de paiement en attente par entreprise,
-- meme sous requetes simultanees ; une reference de paiement ne sert qu'une fois.
CREATE UNIQUE INDEX ux_abo_paiement_attente ON abo_paiement (entreprise_id) WHERE statut = 'EN_ATTENTE';
CREATE UNIQUE INDEX ux_abo_paiement_reference ON abo_paiement (moyen, lower(reference))
    WHERE reference IS NOT NULL AND statut <> 'REJETE';

-- Finance du parc : une mission ou un contrat n'a qu'une facture active.
CREATE UNIQUE INDEX ux_facture_mission_active ON facture (entreprise_id, mission_id)
    WHERE mission_id IS NOT NULL AND statut <> 'ANNULEE';
CREATE UNIQUE INDEX ux_facture_location_active ON facture (entreprise_id, location_id)
    WHERE location_id IS NOT NULL AND statut <> 'ANNULEE';

-- Point de vente : remise accordee par un caissier plafonnee (le gerant et l'administrateur
-- ne sont pas plafonnes) ; 100 desactive le plafond.
ALTER TABLE pdv_reglage ADD COLUMN remise_max_caissier_pourcent NUMERIC(5, 2) NOT NULL DEFAULT 10;
ALTER TABLE pdv_reglage ADD CONSTRAINT ck_pdv_reglage_remise_max
    CHECK (remise_max_caissier_pourcent >= 0 AND remise_max_caissier_pourcent <= 100);

-- Point de vente : le stock d'un article ne descend jamais sous zero (defense en profondeur).
ALTER TABLE pdv_article ADD CONSTRAINT ck_pdv_article_stock_positif CHECK (stock_actuel >= 0);
