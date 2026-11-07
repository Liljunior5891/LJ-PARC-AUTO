-- =============================================================================
-- V42 : deux options du point de vente, desactivees par defaut et reglees par
-- l'administrateur.
--
-- 1. Benefices des associes : un compte au profil ASSOCIE (qui garde les droits
--    de l'administrateur) touche un pourcentage de la marge brute des produits
--    (ou categories entieres) auxquels il est rattache.
-- 2. Circuit de caisse : en fin de service, le caissier remet sa recette en
--    especes (billettage) ; le gerant la verifie et la valide, l'argent passe
--    du tiroir a son coffre. Le gerant degage ensuite son coffre vers
--    l'administrateur, qui valide : l'argent arrive sur le compte de l'entreprise.
-- =============================================================================

ALTER TABLE utilisateur_role DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;
ALTER TABLE utilisateur_role ADD CONSTRAINT ck_utilisateur_role_valeur
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR', 'GERANT', 'CAISSIER',
                    'SERVEUR', 'PREPARATEUR', 'MAGASINIER', 'ASSOCIE'));

-- ------------------------------------------------------------------ reglages
CREATE TABLE pdv_reglage (
    id                    BIGSERIAL PRIMARY KEY,
    entreprise_id         BIGINT NOT NULL REFERENCES entreprise (id),
    associes_actif        BOOLEAN NOT NULL DEFAULT FALSE,
    circuit_caisse_actif  BOOLEAN NOT NULL DEFAULT FALSE,
    -- Ecart (en valeur absolue) au-dela duquel une justification est exigee.
    ecart_tolere          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    cree_le               TIMESTAMPTZ,
    cree_par              VARCHAR(100),
    modifie_le            TIMESTAMPTZ,
    modifie_par           VARCHAR(100),
    version               INTEGER,
    CONSTRAINT uk_pdv_reglage_entreprise UNIQUE (entreprise_id),
    CONSTRAINT ck_pdv_reglage_ecart CHECK (ecart_tolere >= 0)
);

-- ---------------------------------------------------------- parts des associes
CREATE TABLE pdv_associe_part (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    associe_id     BIGINT NOT NULL REFERENCES utilisateur (id),
    article_id     BIGINT REFERENCES pdv_article (id),
    categorie_id   BIGINT REFERENCES pdv_categorie (id),
    taux           NUMERIC(5, 2) NOT NULL,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_associe_part_cible CHECK ((article_id IS NULL) <> (categorie_id IS NULL)),
    CONSTRAINT ck_pdv_associe_part_taux CHECK (taux > 0 AND taux <= 100)
);
CREATE UNIQUE INDEX uk_pdv_associe_part_article ON pdv_associe_part (associe_id, article_id) WHERE article_id IS NOT NULL;
CREATE UNIQUE INDEX uk_pdv_associe_part_categorie ON pdv_associe_part (associe_id, categorie_id) WHERE categorie_id IS NOT NULL;

-- ------------------------------------------------- remises des caissiers
CREATE TABLE pdv_remise_caisse (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    numero              VARCHAR(40) NOT NULL,
    session_id          BIGINT NOT NULL REFERENCES pdv_session_caisse (id),
    caissier_id         BIGINT NOT NULL,
    caissier_nom        VARCHAR(150) NOT NULL,
    -- Especes attendues dans le tiroir, fond de caisse deduit, au moment de l'envoi.
    montant_attendu     NUMERIC(16, 2) NOT NULL,
    montant_declare     NUMERIC(16, 2) NOT NULL,
    -- Montant recompte par celui qui valide : c'est lui qui quitte la caisse.
    montant_valide      NUMERIC(16, 2),
    justification       TEXT,
    statut              VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    envoyee_le          TIMESTAMPTZ NOT NULL,
    traitee_par_id      BIGINT,
    traitee_par_nom     VARCHAR(150),
    traitee_le          TIMESTAMPTZ,
    commentaire         TEXT,
    -- Coffre du gerant qui a valide ; vide si l'administrateur a valide (compte de l'entreprise).
    coffre_id           BIGINT,
    mouvement_caisse_id BIGINT REFERENCES pdv_mouvement_caisse (id) ON DELETE SET NULL,
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_pdv_remise_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_remise_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDEE', 'REFUSEE'))
);
CREATE INDEX idx_pdv_remise_session ON pdv_remise_caisse (session_id, statut);
CREATE INDEX idx_pdv_remise_coffre ON pdv_remise_caisse (coffre_id, statut);

CREATE TABLE pdv_remise_coupure (
    remise_id  BIGINT NOT NULL REFERENCES pdv_remise_caisse (id) ON DELETE CASCADE,
    moment     VARCHAR(20) NOT NULL,
    valeur     NUMERIC(12, 2) NOT NULL,
    quantite   INTEGER NOT NULL,
    CONSTRAINT ck_pdv_remise_coupure_moment CHECK (moment IN ('DECLARE', 'VERIFIE'))
);
CREATE INDEX idx_pdv_remise_coupure ON pdv_remise_coupure (remise_id);

-- ----------------------------------------- degagements du coffre du gerant
CREATE TABLE pdv_degagement (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    numero           VARCHAR(40) NOT NULL,
    gerant_id        BIGINT NOT NULL,
    gerant_nom       VARCHAR(150) NOT NULL,
    -- Solde du coffre au moment de l'envoi.
    montant_attendu  NUMERIC(16, 2) NOT NULL,
    montant_declare  NUMERIC(16, 2) NOT NULL,
    montant_valide   NUMERIC(16, 2),
    justification    TEXT,
    statut           VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    envoye_le        TIMESTAMPTZ NOT NULL,
    traite_par_id    BIGINT,
    traite_par_nom   VARCHAR(150),
    traite_le        TIMESTAMPTZ,
    commentaire      TEXT,
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_pdv_degagement_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_degagement_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'REFUSE'))
);
CREATE INDEX idx_pdv_degagement_gerant ON pdv_degagement (gerant_id, statut);

CREATE TABLE pdv_degagement_coupure (
    degagement_id  BIGINT NOT NULL REFERENCES pdv_degagement (id) ON DELETE CASCADE,
    moment         VARCHAR(20) NOT NULL,
    valeur         NUMERIC(12, 2) NOT NULL,
    quantite       INTEGER NOT NULL,
    CONSTRAINT ck_pdv_degagement_coupure_moment CHECK (moment IN ('DECLARE', 'VERIFIE'))
);
CREATE INDEX idx_pdv_degagement_coupure ON pdv_degagement_coupure (degagement_id);
