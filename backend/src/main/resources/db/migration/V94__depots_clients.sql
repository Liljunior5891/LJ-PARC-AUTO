-- =============================================================================
-- V94 : depot d'argent des clients (quincaillerie).
--
-- Un client verse de l'argent sans rien acheter (especes dans la caisse ouverte,
-- mobile money, carte) : un recu de depot lui est remis. Plus tard, a la caisse,
-- son solde paie tout ou partie d'un achat (pdv_vente.paye_par_depot).
-- Le solde non utilise peut lui etre rembourse : demande par le caissier, validee
-- par le gerant, depuis la caisse (sortie d'especes) ou la banque.
--
-- Solde = depots valides - achats payes par le depot - remboursements valides
--         ou en attente.
-- =============================================================================

CREATE TABLE pdv_depot_client (
    id                 BIGSERIAL PRIMARY KEY,
    entreprise_id      BIGINT         NOT NULL REFERENCES entreprise (id),
    numero             VARCHAR(40)    NOT NULL,
    client_id          BIGINT         NOT NULL REFERENCES pdv_client (id),
    -- DEPOT : argent recu du client ; REMBOURSEMENT : argent rendu au client.
    type               VARCHAR(20)    NOT NULL,
    montant            NUMERIC(16, 2) NOT NULL,
    -- Depot : moyen de paiement du client. Remboursement par la caisse : ESPECES.
    mode_paiement      VARCHAR(20),
    -- CAISSE : session de caisse ; BANQUE : compte de l'entreprise (remboursement seulement).
    source             VARCHAR(20)    NOT NULL,
    session_id         BIGINT REFERENCES pdv_session_caisse (id),
    montant_recu       NUMERIC(16, 2),
    reference_paiement VARCHAR(80),
    motif              VARCHAR(500),
    statut             VARCHAR(20)    NOT NULL,
    auteur_id          BIGINT         NOT NULL,
    auteur_nom         VARCHAR(150)   NOT NULL,
    date_operation     TIMESTAMPTZ    NOT NULL,
    traite_par         VARCHAR(150),
    traite_le          TIMESTAMPTZ,
    motif_refus        VARCHAR(500),
    -- Remboursement par la caisse : la sortie d'especes creee a la validation.
    mouvement_id       BIGINT REFERENCES pdv_mouvement_caisse (id),
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER,
    CONSTRAINT uk_pdv_depot_client_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_depot_client_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_depot_client_type CHECK (type IN ('DEPOT', 'REMBOURSEMENT')),
    CONSTRAINT ck_pdv_depot_client_source CHECK (source IN ('CAISSE', 'BANQUE')),
    CONSTRAINT ck_pdv_depot_client_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'REFUSE', 'ANNULE')),
    -- Un depot entre toujours par une session de caisse, avec son moyen de paiement.
    CONSTRAINT ck_pdv_depot_client_depot CHECK (type <> 'DEPOT'
        OR (source = 'CAISSE' AND session_id IS NOT NULL AND mode_paiement IS NOT NULL)),
    CONSTRAINT ck_pdv_depot_client_caisse CHECK (source <> 'CAISSE' OR session_id IS NOT NULL)
);
CREATE INDEX idx_pdv_depot_client ON pdv_depot_client (entreprise_id, client_id, statut);
CREATE INDEX idx_pdv_depot_client_session ON pdv_depot_client (session_id);

-- Part d'un achat payee avec le depot du client : elle n'entre pas dans la caisse
-- (l'argent y est entre au depot).
ALTER TABLE pdv_vente ADD COLUMN paye_par_depot NUMERIC(16, 2) NOT NULL DEFAULT 0;
ALTER TABLE pdv_vente ADD CONSTRAINT ck_pdv_vente_paye_par_depot CHECK (paye_par_depot >= 0);
