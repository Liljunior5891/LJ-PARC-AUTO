-- =============================================================================
-- V53 : rapprochement des commissions avec les versements des operateurs.
--
-- Une commission est acquise des la transaction (elle compte dans le chiffre
-- d'affaires) mais, pour un service a versement differe, aucun compte n'est
-- credite : elle reste en attente jusqu'a ce que l'operateur la verse. Le
-- versement enregistre le montant recu, le compte credite et les commissions
-- qu'il couvre ; l'ecart entre attendu et verse est calcule.
--
-- Tous les services passent en versement differe ; un service peut revenir au
-- credit immediat sur le sous-compte des commissions.
-- =============================================================================

ALTER TABLE agc_service ADD COLUMN commission_differee BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE agc_service SET commission_differee = TRUE;

CREATE TABLE agc_versement_commission (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id              BIGINT NOT NULL REFERENCES agc_agence (id),
    service_id             BIGINT NOT NULL REFERENCES agc_service (id),
    reference              VARCHAR(40) NOT NULL,
    reference_operateur    VARCHAR(80),
    date_versement         TIMESTAMPTZ NOT NULL,
    journee                DATE NOT NULL,
    montant_attendu        BIGINT NOT NULL,
    montant_verse          BIGINT NOT NULL,
    ecart                  BIGINT NOT NULL,
    nombre_commissions     INTEGER NOT NULL,
    compte_id              BIGINT NOT NULL REFERENCES agc_compte (id),
    observations           VARCHAR(500),
    statut                 VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    auteur_nom             VARCHAR(150),
    ecriture_id            BIGINT REFERENCES agc_ecriture (id),
    annulation_ecriture_id BIGINT REFERENCES agc_ecriture (id),
    motif_annulation       VARCHAR(500),
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_agc_versement_reference UNIQUE (entreprise_id, reference),
    CONSTRAINT ck_agc_versement_montants CHECK (montant_attendu >= 0 AND montant_verse >= 0),
    CONSTRAINT ck_agc_versement_statut CHECK (statut IN ('VALIDE', 'ANNULE'))
);
CREATE INDEX ix_agc_versement_agence ON agc_versement_commission (agence_id, service_id, date_versement DESC);

-- Etat de la commission de chaque transaction : ACQUISE (en attente du versement) ou CREDITEE.
-- Les transactions existantes ont ete creditees aussitot.
ALTER TABLE agc_transaction ADD COLUMN commission_statut VARCHAR(20) NOT NULL DEFAULT 'CREDITEE';
ALTER TABLE agc_transaction ADD COLUMN versement_id BIGINT REFERENCES agc_versement_commission (id);
ALTER TABLE agc_transaction ADD CONSTRAINT ck_agc_transaction_commission_statut CHECK (commission_statut IN ('ACQUISE', 'CREDITEE'));
CREATE INDEX ix_agc_transaction_commission_attente ON agc_transaction (agence_id, service_id, commission_statut);

-- Les versements de commission s'ecrivent au registre sous leur propre nature.
ALTER TABLE agc_ecriture DROP CONSTRAINT ck_agc_ecriture_nature;
ALTER TABLE agc_ecriture ADD CONSTRAINT ck_agc_ecriture_nature CHECK (nature IN ('OUVERTURE', 'TRANSACTION', 'MOUVEMENT',
    'REGULARISATION', 'ANNULATION', 'DEPENSE', 'CREDIT', 'CARTE', 'COMMISSION'));

-- Alerte des commissions non creditees au-dela de ce nombre de jours.
ALTER TABLE agc_reglage ADD COLUMN jours_alerte_commission INTEGER NOT NULL DEFAULT 30;
ALTER TABLE agc_reglage ADD CONSTRAINT ck_agc_reglage_commission CHECK (jours_alerte_commission BETWEEN 0 AND 365);

-- Nouveau droit : rapprocher les commissions, accorde par defaut au superviseur.
INSERT INTO agc_permission (entreprise_id, role, permission)
SELECT entreprise_id, 'SUPERVISEUR_AGENCE', 'COMMISSION_RAPPROCHER' FROM agc_reglage
ON CONFLICT DO NOTHING;
