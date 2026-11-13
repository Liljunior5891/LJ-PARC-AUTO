-- =============================================================================
-- V87 : versements du fonds de reserve figes a la cloture de chaque mois.
-- Tant que le mois court, son versement est une estimation (non disponible et non
-- retiree du compte de l'entreprise). Le mois termine, le calcul est enregistre une
-- fois pour toutes : une vente annulee ou une depense antidatee apres coup ne
-- change plus un mois deja verse, eventuellement deja depense.
-- =============================================================================

CREATE TABLE pdv_fonds_reserve_mois (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT         NOT NULL REFERENCES entreprise (id),
    -- Tranche d'un mois couverte par une periode d'application (un changement de
    -- taux en cours de mois donne deux tranches).
    debut            DATE           NOT NULL,
    fin              DATE           NOT NULL,
    pourcent         NUMERIC(5, 2)  NOT NULL,
    chiffre_affaires NUMERIC(16, 2) NOT NULL,
    cout_achats      NUMERIC(16, 2) NOT NULL,
    depenses         NUMERIC(16, 2) NOT NULL,
    benefice_net     NUMERIC(16, 2) NOT NULL,
    -- Pourcentage du benefice net, et ce qui a reellement ete verse (une reprise ne
    -- depasse pas le cumul deja verse).
    dotation         NUMERIC(16, 2) NOT NULL,
    verse            NUMERIC(16, 2) NOT NULL,
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT ck_pdv_fonds_reserve_mois_dates CHECK (fin >= debut),
    CONSTRAINT uk_pdv_fonds_reserve_mois UNIQUE (entreprise_id, debut)
);

-- Les depenses recurrentes ne se paient pas par le fonds de reserve : son
-- disponible se decide a chaque fois, par l'administrateur.
ALTER TABLE pdv_depense_recurrente ADD CONSTRAINT ck_pdv_depense_rec_mode
    CHECK (mode IS NULL OR mode <> 'RESERVE');
