-- =============================================================================
-- V19 : commandes de reapprovisionnement aupres des fournisseurs.
--
-- Le gerant passe une commande (fournisseur, produits, quantites, couts) ; la
-- marchandise est receptionnee plus tard : les quantites effectivement recues
-- entrent en stock a cette date, au cout de la commande, qui devient le cout
-- d'achat de reference des produits.
-- =============================================================================

CREATE TABLE pdv_commande_fournisseur (
    id                BIGSERIAL PRIMARY KEY,
    entreprise_id     BIGINT NOT NULL REFERENCES entreprise (id),
    numero            VARCHAR(40) NOT NULL,
    fournisseur       VARCHAR(150) NOT NULL,
    date_commande     DATE NOT NULL,
    livraison_prevue  DATE,
    statut            VARCHAR(20) NOT NULL,
    note              VARCHAR(500),
    recue_le          TIMESTAMPTZ,
    recue_par         VARCHAR(150),
    annulee_le        TIMESTAMPTZ,
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER,
    CONSTRAINT uk_pdv_commande_fournisseur_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_commande_fournisseur_statut CHECK (statut IN ('EN_COURS', 'RECUE', 'ANNULEE'))
);
CREATE INDEX idx_pdv_commande_fournisseur ON pdv_commande_fournisseur (entreprise_id, date_commande DESC);

CREATE TABLE pdv_ligne_commande_fournisseur (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    commande_id      BIGINT NOT NULL REFERENCES pdv_commande_fournisseur (id) ON DELETE CASCADE,
    article_id       BIGINT NOT NULL REFERENCES pdv_article (id),
    quantite         NUMERIC(14, 3) NOT NULL,
    cout_unitaire    NUMERIC(16, 2),
    -- Renseignee a la reception : peut differer de la quantite commandee.
    quantite_recue   NUMERIC(14, 3),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT ck_pdv_ligne_cf_quantite CHECK (quantite > 0),
    CONSTRAINT ck_pdv_ligne_cf_recue CHECK (quantite_recue IS NULL OR quantite_recue >= 0)
);
CREATE INDEX idx_pdv_ligne_cf_commande ON pdv_ligne_commande_fournisseur (commande_id);
