-- =============================================================================
-- V46 : baremes de commission des agences.
--
-- Un bareme s'applique a un service, et au choix a un seul type d'operation
-- et/ou a un seul reseau ; le plus precis l'emporte. Il calcule un montant fixe,
-- un pourcentage (avec plancher et plafond) ou un montant par tranche de
-- montant, chaque tranche ayant son fixe ou son pourcentage : un bareme peut
-- donc ne pas etre lineaire. Il vaut sur une periode : le modifier en cree une
-- nouvelle version a partir de sa date d'effet, l'ancienne reste consultable.
-- La commission d'une transaction est figee a sa validation : un changement de
-- bareme ne recalcule jamais le passe.
-- =============================================================================

CREATE TABLE agc_bareme (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    service_id          BIGINT NOT NULL REFERENCES agc_service (id),
    -- Nul : toutes les operations du service.
    type_operation_id   BIGINT REFERENCES agc_type_operation (id),
    -- Nul : tous les reseaux du service.
    reseau_id           BIGINT REFERENCES agc_reseau (id),
    libelle             VARCHAR(120) NOT NULL,
    mode                VARCHAR(20) NOT NULL,
    valeur_fixe         BIGINT,
    taux                NUMERIC(9, 4),
    minimum             BIGINT,
    maximum             BIGINT,
    -- Compte credite : le sous-compte des commissions, ou le float du service.
    compte_credit       VARCHAR(20) NOT NULL DEFAULT 'COMMISSIONS',
    date_debut          DATE NOT NULL,
    date_fin            DATE,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    -- Version precedente de ce bareme, pour l'historique.
    precedent_id        BIGINT REFERENCES agc_bareme (id),
    -- Bareme d'exemple (cahier des charges), a verifier avant usage.
    exemple             BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT ck_agc_bareme_mode CHECK (mode IN ('FIXE', 'POURCENTAGE', 'TRANCHES')),
    CONSTRAINT ck_agc_bareme_compte CHECK (compte_credit IN ('COMMISSIONS', 'FLOAT')),
    CONSTRAINT ck_agc_bareme_periode CHECK (date_fin IS NULL OR date_fin >= date_debut),
    CONSTRAINT ck_agc_bareme_valeurs CHECK ((valeur_fixe IS NULL OR valeur_fixe >= 0) AND (taux IS NULL OR taux >= 0)
        AND (minimum IS NULL OR minimum >= 0) AND (maximum IS NULL OR maximum >= 0))
);
CREATE INDEX ix_agc_bareme_service ON agc_bareme (service_id, date_debut);

CREATE TABLE agc_bareme_tranche (
    id             BIGSERIAL PRIMARY KEY,
    bareme_id      BIGINT NOT NULL REFERENCES agc_bareme (id) ON DELETE CASCADE,
    montant_min    BIGINT NOT NULL,
    -- Nul : sans limite haute.
    montant_max    BIGINT,
    mode           VARCHAR(20) NOT NULL,
    valeur_fixe    BIGINT,
    taux           NUMERIC(9, 4),
    CONSTRAINT ck_agc_tranche_mode CHECK (mode IN ('FIXE', 'POURCENTAGE')),
    CONSTRAINT ck_agc_tranche_bornes CHECK (montant_min >= 0 AND (montant_max IS NULL OR montant_max >= montant_min))
);
CREATE INDEX ix_agc_tranche_bareme ON agc_bareme_tranche (bareme_id);
