-- =============================================================================
-- V95 : avances aux fournisseurs et reception d'une commande en plusieurs fois.
--
-- Avance : l'entreprise verse de l'argent a un fournisseur sans marchandise
-- (caisse, coffre ou banque), eventuellement pour une commande precise. A chaque
-- reception, la dette nee de la marchandise recue est reglee d'office avec
-- l'avance disponible (reglement de mode AVANCE : aucun argent ne bouge alors).
-- Le fournisseur peut rembourser une avance (sur le compte de l'entreprise).
--
-- Reception partielle : une commande recoit sa marchandise en plusieurs fois
-- (statut PARTIELLE) ; chaque reception ajoute sa valeur a la dette. Le gerant
-- peut solder la commande : le reste a recevoir est abandonne.
-- =============================================================================

ALTER TABLE pdv_commande_fournisseur DROP CONSTRAINT ck_pdv_commande_fournisseur_statut;
ALTER TABLE pdv_commande_fournisseur ADD CONSTRAINT ck_pdv_commande_fournisseur_statut
    CHECK (statut IN ('EN_COURS', 'PARTIELLE', 'RECUE', 'ANNULEE'));
ALTER TABLE pdv_commande_fournisseur
    ADD COLUMN soldee_le   TIMESTAMPTZ,
    ADD COLUMN soldee_par  VARCHAR(150),
    ADD COLUMN motif_solde VARCHAR(255),
    ADD COLUMN receptions  INTEGER NOT NULL DEFAULT 0;
UPDATE pdv_commande_fournisseur SET receptions = 1 WHERE statut = 'RECUE';

-- Reglement d'une dette avec l'avance deja versee au fournisseur.
ALTER TABLE pdv_reglement_fournisseur DROP CONSTRAINT ck_pdv_reglement_frs_mode;
ALTER TABLE pdv_reglement_fournisseur ADD CONSTRAINT ck_pdv_reglement_frs_mode
    CHECK (mode IN ('CAISSE', 'COFFRE', 'BANQUE', 'ESPECES', 'MOBILE_MONEY', 'AVANCE'));

CREATE TABLE pdv_avance_fournisseur (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT         NOT NULL REFERENCES entreprise (id),
    numero              VARCHAR(40)    NOT NULL,
    fournisseur_id      BIGINT         NOT NULL REFERENCES pdv_fournisseur (id),
    -- Commande pour laquelle l'avance est versee (facultatif, a titre d'information).
    commande_id         BIGINT REFERENCES pdv_commande_fournisseur (id),
    -- AVANCE : argent verse au fournisseur ; REMBOURSEMENT : argent rendu par lui.
    type                VARCHAR(20)    NOT NULL,
    montant             NUMERIC(16, 2) NOT NULL,
    mode                VARCHAR(20)    NOT NULL,
    session_id          BIGINT REFERENCES pdv_session_caisse (id),
    mouvement_caisse_id BIGINT REFERENCES pdv_mouvement_caisse (id),
    coffre_id           BIGINT,
    reference           VARCHAR(80),
    date_operation      DATE           NOT NULL,
    note                VARCHAR(255),
    statut              VARCHAR(20)    NOT NULL DEFAULT 'VALIDE',
    auteur_nom          VARCHAR(150)   NOT NULL,
    annule_le           TIMESTAMPTZ,
    annule_par          VARCHAR(150),
    motif_annulation    VARCHAR(255),
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_pdv_avance_fournisseur_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_avance_fournisseur_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_avance_fournisseur_type CHECK (type IN ('AVANCE', 'REMBOURSEMENT')),
    CONSTRAINT ck_pdv_avance_fournisseur_mode CHECK (mode IN ('CAISSE', 'COFFRE', 'BANQUE')),
    CONSTRAINT ck_pdv_avance_fournisseur_statut CHECK (statut IN ('VALIDE', 'ANNULE')),
    -- Le fournisseur rembourse sur le compte de l'entreprise.
    CONSTRAINT ck_pdv_avance_fournisseur_remb CHECK (type <> 'REMBOURSEMENT' OR mode = 'BANQUE')
);
CREATE INDEX idx_pdv_avance_fournisseur ON pdv_avance_fournisseur (entreprise_id, fournisseur_id, statut);
