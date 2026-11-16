-- =============================================================================
-- V96 : chaque reception d'une commande fournisseur est datee a part.
--
-- Une commande recue en plusieurs fois compte ses achats au mois de chaque
-- reception (tableau de bord : achats recus), et non en entier au mois de la
-- derniere. Les commandes deja recues gardent une reception unique a leur date.
--
-- Solde du reste d'une vente payee avec le depot du client : la part payee par
-- le depot lui revient sur son compte (montant_rendu_depot), pas par la caisse.
-- =============================================================================

ALTER TABLE pdv_solde_reste ADD COLUMN montant_rendu_depot NUMERIC(16, 2) NOT NULL DEFAULT 0;

CREATE TABLE pdv_reception_commande (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT         NOT NULL REFERENCES entreprise (id),
    commande_id   BIGINT         NOT NULL REFERENCES pdv_commande_fournisseur (id),
    recue_le      TIMESTAMPTZ    NOT NULL,
    recue_par     VARCHAR(150),
    -- Valeur de la marchandise arrivee a cette reception (quantite x cout).
    valeur        NUMERIC(16, 2) NOT NULL DEFAULT 0,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER
);
CREATE INDEX idx_pdv_reception_commande ON pdv_reception_commande (entreprise_id, recue_le);
CREATE INDEX idx_pdv_reception_commande_cmd ON pdv_reception_commande (commande_id);

INSERT INTO pdv_reception_commande (entreprise_id, commande_id, recue_le, recue_par, valeur, cree_le, version)
SELECT c.entreprise_id, c.id, c.recue_le, c.recue_par,
       COALESCE((SELECT SUM(l.quantite_recue * COALESCE(l.cout_unitaire, 0))
                 FROM pdv_ligne_commande_fournisseur l WHERE l.commande_id = c.id), 0),
       c.recue_le, 0
FROM pdv_commande_fournisseur c
WHERE c.recue_le IS NOT NULL AND c.statut IN ('PARTIELLE', 'RECUE');
