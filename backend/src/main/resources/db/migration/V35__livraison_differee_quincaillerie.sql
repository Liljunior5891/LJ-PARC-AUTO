-- Quincaillerie : le client peut emporter une partie de sa commande et revenir chercher le reste.
-- Le stock sort a la remise ; ce qui est vendu mais pas encore remis reste reserve.

ALTER TABLE pdv_article ADD COLUMN stock_reserve NUMERIC(14, 3) NOT NULL DEFAULT 0;
ALTER TABLE pdv_article ADD CONSTRAINT ck_pdv_article_reserve CHECK (stock_reserve >= 0);

-- Toutes les ventes passees ont ete remises en entier.
ALTER TABLE pdv_ligne_vente
    ADD COLUMN quantite_livree      NUMERIC(14, 3),
    ADD COLUMN quantite_base_livree NUMERIC(14, 3);
UPDATE pdv_ligne_vente SET quantite_livree = quantite, quantite_base_livree = quantite_base;
ALTER TABLE pdv_ligne_vente
    ALTER COLUMN quantite_livree SET NOT NULL,
    ALTER COLUMN quantite_base_livree SET NOT NULL,
    ADD CONSTRAINT ck_pdv_ligne_vente_livree CHECK (quantite_livree >= 0 AND quantite_livree <= quantite);
CREATE INDEX idx_pdv_ligne_vente_a_livrer ON pdv_ligne_vente (vente_id) WHERE quantite_livree < quantite;

ALTER TABLE pdv_reglage_materiaux ADD COLUMN livraison_differee BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE pdv_bon_livraison (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    numero           VARCHAR(40) NOT NULL,
    vente_id         BIGINT NOT NULL REFERENCES pdv_vente (id),
    date_bon         TIMESTAMPTZ NOT NULL,
    remis_par        VARCHAR(150) NOT NULL,
    -- Personne qui a pris la marchandise : le client, son chauffeur, son chef de chantier.
    recu_par         VARCHAR(150),
    note             VARCHAR(255),
    statut           VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    annule_le        TIMESTAMPTZ,
    annule_par       VARCHAR(150),
    motif_annulation VARCHAR(255),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_pdv_bon_livraison_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_bon_livraison_statut CHECK (statut IN ('VALIDE', 'ANNULE'))
);
CREATE INDEX idx_pdv_bon_livraison_vente ON pdv_bon_livraison (vente_id);

CREATE TABLE pdv_ligne_bon_livraison (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    bon_id         BIGINT NOT NULL REFERENCES pdv_bon_livraison (id) ON DELETE CASCADE,
    ligne_vente_id BIGINT NOT NULL REFERENCES pdv_ligne_vente (id),
    quantite       NUMERIC(14, 3) NOT NULL,
    quantite_base  NUMERIC(14, 3) NOT NULL,
    -- Reste a livrer juste apres ce bon : le document reimprime ne change pas ensuite.
    reste_apres    NUMERIC(14, 3) NOT NULL,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_ligne_bon_quantite CHECK (quantite > 0)
);
CREATE INDEX idx_pdv_ligne_bon_bon ON pdv_ligne_bon_livraison (bon_id);
