-- Reglage : vendre plus que le disponible, le reste etant livre a l'arrivage de la marchandise.
ALTER TABLE pdv_reglage_materiaux ADD COLUMN vente_sur_arrivage BOOLEAN NOT NULL DEFAULT FALSE;

-- Un client peut renoncer a une ligne dont il n'a rien emporte : elle tombe a zero, sans disparaitre.
ALTER TABLE pdv_ligne_vente DROP CONSTRAINT ck_pdv_ligne_quantite;
ALTER TABLE pdv_ligne_vente ADD CONSTRAINT ck_pdv_ligne_quantite CHECK (quantite >= 0);

-- Solde du reste : le client renonce a ce qu'il n'a pas emporte. La valeur diminue ce qu'il doit,
-- le surplus lui est rembourse sur une session de caisse ouverte.
CREATE TABLE pdv_solde_reste (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    numero              VARCHAR(40) NOT NULL,
    vente_id            BIGINT NOT NULL REFERENCES pdv_vente (id),
    -- Nulle quand rien n'est rembourse : la valeur n'a fait que diminuer le reste du.
    session_id          BIGINT REFERENCES pdv_session_caisse (id),
    date_solde          TIMESTAMPTZ NOT NULL,
    auteur_nom          VARCHAR(150) NOT NULL,
    motif               VARCHAR(255) NOT NULL,
    -- Valeur retiree des lignes, puis valeur nette apres la remise globale de la vente.
    montant_brut        NUMERIC(16, 2) NOT NULL,
    montant             NUMERIC(16, 2) NOT NULL,
    montant_deduit      NUMERIC(16, 2) NOT NULL,
    montant_rembourse   NUMERIC(16, 2) NOT NULL,
    mode_remboursement  VARCHAR(20),
    reference_paiement  VARCHAR(80),
    statut              VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    annule_le           TIMESTAMPTZ,
    annule_par          VARCHAR(150),
    motif_annulation    VARCHAR(255),
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_pdv_solde_reste_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_solde_reste_statut CHECK (statut IN ('VALIDE', 'ANNULE')),
    CONSTRAINT ck_pdv_solde_reste_montants CHECK (montant_deduit >= 0 AND montant_rembourse >= 0),
    CONSTRAINT ck_pdv_solde_reste_session CHECK (montant_rembourse = 0 OR (session_id IS NOT NULL AND mode_remboursement IS NOT NULL))
);
CREATE INDEX idx_pdv_solde_reste_vente ON pdv_solde_reste (vente_id);
CREATE INDEX idx_pdv_solde_reste_session ON pdv_solde_reste (session_id) WHERE session_id IS NOT NULL;

CREATE TABLE pdv_ligne_solde_reste (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    solde_id       BIGINT NOT NULL REFERENCES pdv_solde_reste (id) ON DELETE CASCADE,
    ligne_vente_id BIGINT NOT NULL REFERENCES pdv_ligne_vente (id),
    quantite       NUMERIC(14, 3) NOT NULL,
    quantite_base  NUMERIC(14, 3) NOT NULL,
    remise         NUMERIC(16, 2) NOT NULL,
    total          NUMERIC(16, 2) NOT NULL,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_ligne_solde_quantite CHECK (quantite > 0)
);
CREATE INDEX idx_pdv_ligne_solde ON pdv_ligne_solde_reste (solde_id);
