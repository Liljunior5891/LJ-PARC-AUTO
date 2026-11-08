-- =============================================================================
-- V51 : cartes prepayees (vente et stock).
--
-- Le stock d'une agence se tient par lot, au cout reel d'achat de chaque lot.
-- Une vente prend les cartes des lots les plus anciens d'abord (premier entre,
-- premier sorti) : la marge brute se calcule sur le cout reel, pas sur le prix
-- d'achat courant. Le prix encaisse va en caisse et forme un chiffre d'affaires ;
-- l'achat d'un lot debite le compte qui le paie. La recharge des cartes passe
-- par les transactions (compte de recharge, commission).
-- =============================================================================

CREATE TABLE agc_produit_carte (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    nom            VARCHAR(120) NOT NULL,
    prix_vente     BIGINT NOT NULL,
    seuil_alerte   INTEGER NOT NULL DEFAULT 2,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_agc_produit_prix CHECK (prix_vente > 0 AND seuil_alerte >= 0)
);
CREATE UNIQUE INDEX uk_agc_produit_carte ON agc_produit_carte (entreprise_id, lower(nom));

CREATE TABLE agc_lot_carte (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id           BIGINT NOT NULL REFERENCES agc_agence (id),
    produit_id          BIGINT NOT NULL REFERENCES agc_produit_carte (id),
    numero              VARCHAR(40) NOT NULL,
    date_entree         TIMESTAMPTZ NOT NULL,
    quantite_initiale   INTEGER NOT NULL,
    quantite_restante   INTEGER NOT NULL,
    cout_unitaire       BIGINT NOT NULL,
    fournisseur         VARCHAR(150),
    reference           VARCHAR(80),
    -- Compte qui a paye le lot ; nul pour le stock initial.
    compte_id           BIGINT REFERENCES agc_compte (id),
    ecriture_id         BIGINT REFERENCES agc_ecriture (id),
    auteur_nom          VARCHAR(150),
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_agc_lot_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_agc_lot_quantites CHECK (quantite_initiale > 0 AND quantite_restante >= 0 AND quantite_restante <= quantite_initiale),
    CONSTRAINT ck_agc_lot_cout CHECK (cout_unitaire >= 0)
);
CREATE INDEX ix_agc_lot_stock ON agc_lot_carte (agence_id, produit_id, date_entree, id);

CREATE TABLE agc_vente_carte (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    produit_id             BIGINT NOT NULL REFERENCES agc_produit_carte (id),
    reference              VARCHAR(40) NOT NULL,
    date_vente             TIMESTAMPTZ NOT NULL,
    journee                DATE NOT NULL,
    quantite               INTEGER NOT NULL,
    prix_unitaire          BIGINT NOT NULL,
    montant                BIGINT NOT NULL,
    -- Cout reel des cartes vendues (lots les plus anciens d'abord) et marge brute.
    cout_total             BIGINT NOT NULL,
    marge                  BIGINT NOT NULL,
    compte_cash_id         BIGINT NOT NULL REFERENCES agc_compte (id),
    session_id             BIGINT REFERENCES agc_session_caisse (id),
    client_nom             VARCHAR(150),
    client_telephone       VARCHAR(30),
    caissier_id            BIGINT NOT NULL,
    caissier_nom           VARCHAR(150) NOT NULL,
    statut                 VARCHAR(20) NOT NULL DEFAULT 'VALIDEE',
    ecriture_id            BIGINT REFERENCES agc_ecriture (id),
    annulation_ecriture_id BIGINT REFERENCES agc_ecriture (id),
    annulee_le             TIMESTAMPTZ,
    annulee_par_nom        VARCHAR(150),
    motif_annulation       VARCHAR(500),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_agc_vente_carte_reference UNIQUE (entreprise_id, reference),
    CONSTRAINT ck_agc_vente_carte CHECK (quantite > 0 AND prix_unitaire >= 0 AND montant >= 0 AND cout_total >= 0),
    CONSTRAINT ck_agc_vente_carte_statut CHECK (statut IN ('VALIDEE', 'ANNULEE'))
);
CREATE INDEX ix_agc_vente_carte_journee ON agc_vente_carte (agence_id, journee);

-- Lots consommes par chaque vente, pour remettre les cartes en stock a l'annulation.
CREATE TABLE agc_vente_carte_lot (
    vente_id       BIGINT NOT NULL REFERENCES agc_vente_carte (id) ON DELETE CASCADE,
    lot_id         BIGINT NOT NULL REFERENCES agc_lot_carte (id),
    quantite       INTEGER NOT NULL,
    cout_unitaire  BIGINT NOT NULL
);
CREATE INDEX ix_agc_vente_carte_lot ON agc_vente_carte_lot (vente_id);

-- Nouveau droit : approvisionner le stock de cartes, accorde par defaut au superviseur.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'CARTE_STOCK' FROM agc_reglage
ON CONFLICT DO NOTHING;
