-- =============================================================================
-- V88 : ventes de la caisse du point de vente faites sans connexion.
-- L'appareil garde chaque vente (especes seules) et l'envoie au retour du
-- reseau. Le serveur la rejoue au prix encaisse ; un manque de stock ne la
-- refuse pas (la vente a eu lieu) : le stock descend au plus a zero et la
-- quantite manquante est signalee au gerant. Une vente qui ne peut pas etre
-- enregistree (session fermee entre-temps, article supprime...) devient un
-- conflit que le gerant reprend ou abandonne.
-- =============================================================================

ALTER TABLE pdv_vente ADD COLUMN hors_ligne BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pdv_vente ADD COLUMN numero_provisoire VARCHAR(40);

CREATE TABLE pdv_vente_hors_ligne (
    id                BIGSERIAL PRIMARY KEY,
    entreprise_id     BIGINT         NOT NULL REFERENCES entreprise (id),
    -- Cle unique tiree par l'appareil : un renvoi ne cree jamais deux ventes.
    cle               VARCHAR(64)    NOT NULL,
    numero_provisoire VARCHAR(40)    NOT NULL,
    saisie_le         TIMESTAMPTZ    NOT NULL,
    caissier_nom      VARCHAR(150)   NOT NULL,
    session_id        BIGINT,
    total             NUMERIC(16, 2) NOT NULL,
    -- La vente telle que l'appareil l'a envoyee (JSON), pour la reprise d'un conflit.
    contenu           TEXT           NOT NULL,
    statut            VARCHAR(20)    NOT NULL,
    vente_id          BIGINT REFERENCES pdv_vente (id),
    message           VARCHAR(500),
    -- Ce que le gerant doit regulariser : stock manquant, prix different du catalogue.
    manques           VARCHAR(1000),
    ecarts_prix       VARCHAR(1000),
    traitee_par       VARCHAR(150),
    traitee_le        TIMESTAMPTZ,
    note              VARCHAR(500),
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER,
    CONSTRAINT uk_pdv_vente_hors_ligne_cle UNIQUE (entreprise_id, cle),
    CONSTRAINT ck_pdv_vente_hors_ligne_statut CHECK (statut IN ('ENREGISTREE', 'CONFLIT', 'REPRISE', 'ABANDONNEE'))
);
CREATE INDEX idx_pdv_vente_hors_ligne_statut ON pdv_vente_hors_ligne (entreprise_id, statut, saisie_le DESC);
