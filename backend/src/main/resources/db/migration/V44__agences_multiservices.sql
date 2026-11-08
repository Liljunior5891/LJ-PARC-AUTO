-- =============================================================================
-- V44 : module « Agences multiservices financieres » (Mobile Money, transferts,
-- services financiers, Rapid Transfer, cartes prepayees).
--
-- Une entreprise ouvre une ou plusieurs agences (LJ-Agoe, LJ-Be...), chacune
-- avec ses comptes : caisse cash, float de chaque service, compte de recharge,
-- sous-compte des commissions, banques. Les soldes ne sont jamais saisis : ils
-- decoulent d'un registre de mouvements que personne ne modifie ni ne supprime
-- (une correction passe par une ecriture inverse). Le solde de chaque compte est
-- garde en cache, verrouille pendant chaque ecriture, et se recalcule a partir
-- du registre.
-- =============================================================================

ALTER TABLE utilisateur_role DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;
ALTER TABLE utilisateur_role ADD CONSTRAINT ck_utilisateur_role_valeur
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR', 'GERANT', 'CAISSIER',
                    'SERVEUR', 'PREPARATEUR', 'MAGASINIER', 'ASSOCIE',
                    'SUPERVISEUR_AGENCE', 'CAISSIER_AGENCE'));

-- ------------------------------------------------------------------ reglages
CREATE TABLE agc_reglage (
    id                          BIGSERIAL PRIMARY KEY,
    entreprise_id               BIGINT NOT NULL REFERENCES entreprise (id),
    -- Heure (0 a 23) a laquelle commence la journee de caisse : avant, une operation compte pour la veille.
    heure_bascule               SMALLINT NOT NULL DEFAULT 0,
    -- Meme service, type, montant et client dans ce delai : avertissement de doublon.
    delai_doublon_minutes       INTEGER NOT NULL DEFAULT 2,
    montant_max_transaction     BIGINT NOT NULL DEFAULT 2000000,
    seuil_validation_mouvement  BIGINT NOT NULL DEFAULT 500000,
    plafond_depense_caissier    BIGINT NOT NULL DEFAULT 50000,
    cree_le                     TIMESTAMPTZ,
    cree_par                    VARCHAR(100),
    modifie_le                  TIMESTAMPTZ,
    modifie_par                 VARCHAR(100),
    version                     INTEGER,
    CONSTRAINT uk_agc_reglage_entreprise UNIQUE (entreprise_id),
    CONSTRAINT ck_agc_reglage_bascule CHECK (heure_bascule BETWEEN 0 AND 23),
    CONSTRAINT ck_agc_reglage_doublon CHECK (delai_doublon_minutes >= 0),
    CONSTRAINT ck_agc_reglage_montants CHECK (montant_max_transaction > 0 AND seuil_validation_mouvement >= 0
        AND plafond_depense_caissier >= 0)
);

-- Droits accordes a chaque profil de l'agence ; l'administrateur les a tous.
CREATE TABLE agc_permission (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    role           VARCHAR(40) NOT NULL,
    permission     VARCHAR(40) NOT NULL,
    CONSTRAINT uk_agc_permission UNIQUE (entreprise_id, role, permission)
);

-- ------------------------------------------------------------------- agences
CREATE TABLE agc_agence (
    id              BIGSERIAL PRIMARY KEY,
    entreprise_id   BIGINT NOT NULL REFERENCES entreprise (id),
    code            VARCHAR(20) NOT NULL,
    nom             VARCHAR(120) NOT NULL,
    ville           VARCHAR(80),
    quartier        VARCHAR(80),
    adresse         VARCHAR(255),
    telephone       VARCHAR(30),
    responsable_id  BIGINT REFERENCES utilisateur (id),
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le         TIMESTAMPTZ,
    cree_par        VARCHAR(100),
    modifie_le      TIMESTAMPTZ,
    modifie_par     VARCHAR(100),
    version         INTEGER,
    CONSTRAINT uk_agc_agence_code UNIQUE (entreprise_id, code)
);
CREATE UNIQUE INDEX uk_agc_agence_nom ON agc_agence (entreprise_id, lower(nom));

CREATE TABLE agc_agence_utilisateur (
    agence_id       BIGINT NOT NULL REFERENCES agc_agence (id) ON DELETE CASCADE,
    utilisateur_id  BIGINT NOT NULL REFERENCES utilisateur (id),
    PRIMARY KEY (agence_id, utilisateur_id)
);

-- ------------------------------------------------------- catalogue des services
CREATE TABLE agc_service (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    code           VARCHAR(40) NOT NULL,
    nom            VARCHAR(120) NOT NULL,
    categorie      VARCHAR(60) NOT NULL,
    ordre          INTEGER NOT NULL DEFAULT 0,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_agc_service_code UNIQUE (entreprise_id, code)
);
CREATE UNIQUE INDEX uk_agc_service_nom ON agc_service (entreprise_id, lower(nom));

-- Reseaux d'un service qui en regroupe plusieurs (Western Union, MoneyGram... sous Rapid Transfer).
CREATE TABLE agc_reseau (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    service_id     BIGINT NOT NULL REFERENCES agc_service (id),
    code           VARCHAR(40) NOT NULL,
    nom            VARCHAR(120) NOT NULL,
    ordre          INTEGER NOT NULL DEFAULT 0,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_agc_reseau_code UNIQUE (service_id, code)
);

-- Types d'operation d'un service et leur regle de mouvement : sens du cash et du virtuel.
CREATE TABLE agc_type_operation (
    id              BIGSERIAL PRIMARY KEY,
    entreprise_id   BIGINT NOT NULL REFERENCES entreprise (id),
    service_id      BIGINT NOT NULL REFERENCES agc_service (id),
    code            VARCHAR(40) NOT NULL,
    libelle         VARCHAR(120) NOT NULL,
    -- +1 : le compte augmente du montant ; -1 : il diminue ; 0 : il ne bouge pas.
    effet_cash      SMALLINT NOT NULL,
    effet_virtuel   SMALLINT NOT NULL,
    -- Compte electronique touche : le float du service, son compte de recharge, ou aucun.
    compte_virtuel  VARCHAR(20) NOT NULL,
    -- TRANSIT : l'argent ne fait que passer ; VENTE_CARTE : le prix est un chiffre d'affaires.
    nature          VARCHAR(20) NOT NULL DEFAULT 'TRANSIT',
    ordre           INTEGER NOT NULL DEFAULT 0,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le         TIMESTAMPTZ,
    cree_par        VARCHAR(100),
    modifie_le      TIMESTAMPTZ,
    modifie_par     VARCHAR(100),
    version         INTEGER,
    CONSTRAINT uk_agc_type_operation_code UNIQUE (service_id, code),
    CONSTRAINT ck_agc_type_effets CHECK (effet_cash IN (-1, 0, 1) AND effet_virtuel IN (-1, 0, 1)),
    CONSTRAINT ck_agc_type_compte CHECK (compte_virtuel IN ('FLOAT', 'RECHARGE', 'AUCUN')),
    CONSTRAINT ck_agc_type_coherence CHECK ((compte_virtuel = 'AUCUN') = (effet_virtuel = 0)),
    CONSTRAINT ck_agc_type_nature CHECK (nature IN ('TRANSIT', 'VENTE_CARTE'))
);

-- -------------------------------------------------------------------- comptes
CREATE TABLE agc_compte (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id           BIGINT NOT NULL REFERENCES agc_agence (id),
    type                VARCHAR(30) NOT NULL,
    libelle             VARCHAR(120) NOT NULL,
    service_id          BIGINT REFERENCES agc_service (id),
    utilisateur_id      BIGINT REFERENCES utilisateur (id),
    banque              VARCHAR(120),
    numero_compte       VARCHAR(60),
    -- Solde en cache, toujours egal a la somme des lignes du registre du compte.
    solde               BIGINT NOT NULL DEFAULT 0,
    -- Solde negatif tolere (0 : jamais en dessous de zero).
    decouvert_autorise  BIGINT NOT NULL DEFAULT 0,
    -- Compte bloque : exclu de la tresorerie disponible.
    bloque              BOOLEAN NOT NULL DEFAULT FALSE,
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT ck_agc_compte_type CHECK (type IN ('CAISSE_GENERALE', 'CAISSE_CAISSIER', 'FLOAT', 'RECHARGE',
                                                  'COMMISSIONS', 'BANQUE', 'FONDS_EXPLOITATION')),
    CONSTRAINT ck_agc_compte_service CHECK ((type IN ('FLOAT', 'RECHARGE')) = (service_id IS NOT NULL)),
    CONSTRAINT ck_agc_compte_caissier CHECK ((type = 'CAISSE_CAISSIER') = (utilisateur_id IS NOT NULL)),
    CONSTRAINT ck_agc_compte_decouvert CHECK (decouvert_autorise >= 0)
);
CREATE UNIQUE INDEX uk_agc_compte_unique ON agc_compte (agence_id, type)
    WHERE type IN ('CAISSE_GENERALE', 'COMMISSIONS');
CREATE UNIQUE INDEX uk_agc_compte_service ON agc_compte (agence_id, type, service_id)
    WHERE type IN ('FLOAT', 'RECHARGE');
CREATE UNIQUE INDEX uk_agc_compte_caissier ON agc_compte (agence_id, utilisateur_id)
    WHERE type = 'CAISSE_CAISSIER';
CREATE INDEX ix_agc_compte_agence ON agc_compte (agence_id);

-- ------------------------------------------------------------------- registre
CREATE TABLE agc_ecriture (
    id                   BIGSERIAL PRIMARY KEY,
    entreprise_id        BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id            BIGINT NOT NULL REFERENCES agc_agence (id),
    numero               VARCHAR(40) NOT NULL,
    nature               VARCHAR(30) NOT NULL,
    date_operation       TIMESTAMPTZ NOT NULL,
    -- Journee de caisse, selon l'heure de bascule de l'entreprise.
    journee              DATE NOT NULL,
    libelle              VARCHAR(255) NOT NULL,
    motif                VARCHAR(500),
    -- Piece d'origine (transaction, mouvement...) ; nulle pour une ouverture.
    origine_type         VARCHAR(40),
    origine_id           BIGINT,
    -- Ecriture contre-passee par celle-ci (annulation).
    ecriture_annulee_id  BIGINT REFERENCES agc_ecriture (id),
    auteur_id            BIGINT,
    auteur_nom           VARCHAR(150),
    cree_le              TIMESTAMPTZ,
    cree_par             VARCHAR(100),
    modifie_le           TIMESTAMPTZ,
    modifie_par          VARCHAR(100),
    version              INTEGER,
    CONSTRAINT uk_agc_ecriture_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_agc_ecriture_nature CHECK (nature IN ('OUVERTURE', 'TRANSACTION', 'MOUVEMENT', 'REGULARISATION',
                                                        'ANNULATION', 'DEPENSE', 'CREDIT', 'CARTE'))
);
-- Une ecriture ne s'annule qu'une fois.
CREATE UNIQUE INDEX uk_agc_ecriture_annulee ON agc_ecriture (ecriture_annulee_id) WHERE ecriture_annulee_id IS NOT NULL;
CREATE INDEX ix_agc_ecriture_agence ON agc_ecriture (agence_id, journee);
CREATE INDEX ix_agc_ecriture_origine ON agc_ecriture (origine_type, origine_id);

CREATE TABLE agc_ligne_registre (
    id              BIGSERIAL PRIMARY KEY,
    entreprise_id   BIGINT NOT NULL REFERENCES entreprise (id),
    ecriture_id     BIGINT NOT NULL REFERENCES agc_ecriture (id),
    compte_id       BIGINT NOT NULL REFERENCES agc_compte (id),
    agence_id       BIGINT NOT NULL REFERENCES agc_agence (id),
    service_id      BIGINT REFERENCES agc_service (id),
    reseau_id       BIGINT REFERENCES agc_reseau (id),
    -- Montant signe en francs CFA : positif, le compte augmente.
    montant         BIGINT NOT NULL,
    solde_apres     BIGINT NOT NULL,
    date_operation  TIMESTAMPTZ NOT NULL,
    journee         DATE NOT NULL,
    cree_le         TIMESTAMPTZ,
    cree_par        VARCHAR(100),
    modifie_le      TIMESTAMPTZ,
    modifie_par     VARCHAR(100),
    version         INTEGER,
    CONSTRAINT ck_agc_ligne_montant CHECK (montant <> 0)
);
CREATE INDEX ix_agc_ligne_compte ON agc_ligne_registre (compte_id, id);
CREATE INDEX ix_agc_ligne_compte_journee ON agc_ligne_registre (compte_id, journee);
CREATE INDEX ix_agc_ligne_ecriture ON agc_ligne_registre (ecriture_id);

-- ------------------------------------------------------------ journal d'audit
CREATE TABLE agc_journal_audit (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    agence_id      BIGINT REFERENCES agc_agence (id),
    date_action    TIMESTAMPTZ NOT NULL,
    auteur_id      BIGINT,
    auteur_nom     VARCHAR(150),
    action         VARCHAR(60) NOT NULL,
    objet          VARCHAR(60) NOT NULL,
    objet_id       BIGINT,
    description    VARCHAR(500) NOT NULL,
    avant          TEXT,
    apres          TEXT,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER
);
CREATE INDEX ix_agc_audit_date ON agc_journal_audit (entreprise_id, date_action DESC);

-- ------------------------------------------- registre et audit non modifiables
CREATE OR REPLACE FUNCTION agc_interdire_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Le registre des agences ne se modifie pas : passez par une ecriture inverse (table %)', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_agc_ecriture_immuable BEFORE UPDATE OR DELETE ON agc_ecriture
    FOR EACH ROW EXECUTE FUNCTION agc_interdire_modification();
CREATE TRIGGER tg_agc_ligne_immuable BEFORE UPDATE OR DELETE ON agc_ligne_registre
    FOR EACH ROW EXECUTE FUNCTION agc_interdire_modification();
CREATE TRIGGER tg_agc_audit_immuable BEFORE UPDATE OR DELETE ON agc_journal_audit
    FOR EACH ROW EXECUTE FUNCTION agc_interdire_modification();
