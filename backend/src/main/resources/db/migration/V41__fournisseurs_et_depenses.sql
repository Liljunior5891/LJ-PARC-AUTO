-- =============================================================================
-- V41 : gestion des fournisseurs et des depenses du point de vente.
--
-- Fournisseurs : une fiche par fournisseur, reliee a ses commandes. A la
-- reception, la valeur recue devient une dette, payable en plusieurs fois
-- avant l'echeance (reception + delai de paiement du fournisseur).
--
-- Depenses : salaire, loyer, electricite... classees par categorie (liste de
-- base modifiable par l'administrateur), avec justificatif. Payee depuis la
-- caisse, une depense cree une sortie d'especes dans la session ouverte.
-- Les depenses recurrentes (loyer, salaire) sont proposees chaque mois.
-- =============================================================================

-- --------------------------------------------------------------- fournisseurs
CREATE TABLE pdv_fournisseur (
    id                     BIGSERIAL PRIMARY KEY,
    entreprise_id          BIGINT NOT NULL REFERENCES entreprise (id),
    code                   VARCHAR(40) NOT NULL,
    nom                    VARCHAR(150) NOT NULL,
    personne_contact       VARCHAR(120),
    telephone              VARCHAR(40),
    telephone2             VARCHAR(40),
    email                  VARCHAR(150),
    adresse                VARCHAR(255),
    ville                  VARCHAR(80),
    pays                   VARCHAR(80),
    nif                    VARCHAR(60),
    rccm                   VARCHAR(60),
    -- Jours accordes pour payer apres la reception ; 0 ou vide : paiement a la reception.
    delai_paiement_jours   INTEGER,
    conditions             TEXT,
    note                   VARCHAR(500),
    actif                  BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le                TIMESTAMPTZ,
    cree_par               VARCHAR(100),
    modifie_le             TIMESTAMPTZ,
    modifie_par            VARCHAR(100),
    version                INTEGER,
    CONSTRAINT uk_pdv_fournisseur_code UNIQUE (entreprise_id, code),
    CONSTRAINT ck_pdv_fournisseur_delai CHECK (delai_paiement_jours IS NULL OR delai_paiement_jours >= 0)
);
CREATE INDEX idx_pdv_fournisseur_nom ON pdv_fournisseur (entreprise_id, lower(nom));

-- Une fiche pour chaque fournisseur deja saisi a la main sur les commandes.
INSERT INTO pdv_fournisseur (entreprise_id, code, nom, actif, cree_le, cree_par, version)
SELECT f.entreprise_id, 'FRS-' || EXTRACT(YEAR FROM now())::int || '-' || lpad(f.rang::text, 4, '0'), f.nom, TRUE, now(), 'migration', 0
FROM (SELECT entreprise_id, min(fournisseur) AS nom,
             row_number() OVER (PARTITION BY entreprise_id ORDER BY lower(trim(fournisseur))) AS rang
      FROM pdv_commande_fournisseur
      GROUP BY entreprise_id, lower(trim(fournisseur))) f;

ALTER TABLE pdv_commande_fournisseur
    ADD COLUMN fournisseur_id BIGINT REFERENCES pdv_fournisseur (id),
    -- Valeur recue (quantites recues x cout) : c'est la dette envers le fournisseur.
    ADD COLUMN montant_recu   NUMERIC(16, 2),
    ADD COLUMN echeance       DATE,
    -- Faux pour les commandes recues avant le suivi des dettes : reputees payees.
    ADD COLUMN dette_suivie   BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE pdv_commande_fournisseur c SET fournisseur_id = f.id
FROM pdv_fournisseur f
WHERE f.entreprise_id = c.entreprise_id AND lower(trim(f.nom)) = lower(trim(c.fournisseur));

UPDATE pdv_commande_fournisseur c SET dette_suivie = FALSE,
    montant_recu = (SELECT coalesce(sum(l.quantite_recue * coalesce(l.cout_unitaire, 0)), 0)
                    FROM pdv_ligne_commande_fournisseur l WHERE l.commande_id = c.id)
WHERE c.statut = 'RECUE';

CREATE INDEX idx_pdv_commande_fournisseur_frs ON pdv_commande_fournisseur (fournisseur_id);

-- Les numeros FRS- repris continuent la numerotation de chaque entreprise.
INSERT INTO compteur_numero (entreprise_id, prefixe, annee, valeur)
SELECT entreprise_id, 'FRS', EXTRACT(YEAR FROM now())::int, count(*)
FROM pdv_fournisseur GROUP BY entreprise_id
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------- reglements fournisseurs
CREATE TABLE pdv_reglement_fournisseur (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    numero              VARCHAR(40) NOT NULL,
    fournisseur_id      BIGINT NOT NULL REFERENCES pdv_fournisseur (id),
    commande_id         BIGINT NOT NULL REFERENCES pdv_commande_fournisseur (id),
    montant             NUMERIC(16, 2) NOT NULL,
    mode                VARCHAR(20) NOT NULL,
    reference           VARCHAR(80),
    date_reglement      DATE NOT NULL,
    session_id          BIGINT REFERENCES pdv_session_caisse (id),
    mouvement_caisse_id BIGINT REFERENCES pdv_mouvement_caisse (id) ON DELETE SET NULL,
    auteur_nom          VARCHAR(150) NOT NULL,
    note                VARCHAR(255),
    statut              VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    annule_le           TIMESTAMPTZ,
    annule_par          VARCHAR(150),
    motif_annulation    VARCHAR(255),
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT ck_pdv_reglement_frs_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_reglement_frs_statut CHECK (statut IN ('VALIDE', 'ANNULE')),
    CONSTRAINT ck_pdv_reglement_frs_mode CHECK (mode IN ('CAISSE', 'ESPECES', 'BANQUE', 'MOBILE_MONEY'))
);
CREATE INDEX idx_pdv_reglement_frs_fournisseur ON pdv_reglement_fournisseur (fournisseur_id, date_reglement DESC);
CREATE INDEX idx_pdv_reglement_frs_commande ON pdv_reglement_fournisseur (commande_id);
CREATE INDEX idx_pdv_reglement_frs_numero ON pdv_reglement_fournisseur (entreprise_id, numero);

-- ------------------------------------------------------ categories de depense
CREATE TABLE pdv_categorie_depense (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    nom            VARCHAR(80) NOT NULL,
    icone          VARCHAR(16),
    ordre          INTEGER NOT NULL DEFAULT 0,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER
);
CREATE UNIQUE INDEX uk_pdv_categorie_depense_nom ON pdv_categorie_depense (entreprise_id, lower(nom));

-- ------------------------------------------------------ depenses recurrentes
CREATE TABLE pdv_depense_recurrente (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    libelle        VARCHAR(150) NOT NULL,
    categorie_id   BIGINT NOT NULL REFERENCES pdv_categorie_depense (id),
    beneficiaire   VARCHAR(150),
    montant        NUMERIC(16, 2) NOT NULL,
    jour_echeance  INTEGER NOT NULL DEFAULT 1,
    mode           VARCHAR(20),
    -- Premier et dernier mois dus (le premier jour du mois).
    debut          DATE NOT NULL,
    fin            DATE,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    note           VARCHAR(255),
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_depense_rec_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_depense_rec_jour CHECK (jour_echeance BETWEEN 1 AND 28)
);

-- ------------------------------------------------------------------ depenses
CREATE TABLE pdv_depense (
    id                  BIGSERIAL PRIMARY KEY,
    entreprise_id       BIGINT NOT NULL REFERENCES entreprise (id),
    numero              VARCHAR(40) NOT NULL,
    categorie_id        BIGINT NOT NULL REFERENCES pdv_categorie_depense (id),
    libelle             VARCHAR(150) NOT NULL,
    beneficiaire        VARCHAR(150),
    fournisseur_id      BIGINT REFERENCES pdv_fournisseur (id),
    montant             NUMERIC(16, 2) NOT NULL,
    date_depense        DATE NOT NULL,
    mode                VARCHAR(20) NOT NULL,
    reference           VARCHAR(80),
    session_id          BIGINT REFERENCES pdv_session_caisse (id),
    mouvement_caisse_id BIGINT REFERENCES pdv_mouvement_caisse (id) ON DELETE SET NULL,
    recurrente_id       BIGINT REFERENCES pdv_depense_recurrente (id),
    -- Mois paye (AAAA-MM) pour une depense recurrente.
    periode             VARCHAR(7),
    note                VARCHAR(500),
    justificatif_chemin VARCHAR(255),
    justificatif_nom    VARCHAR(255),
    justificatif_type   VARCHAR(100),
    auteur_nom          VARCHAR(150) NOT NULL,
    statut              VARCHAR(20) NOT NULL DEFAULT 'VALIDE',
    annulee_le          TIMESTAMPTZ,
    annulee_par         VARCHAR(150),
    motif_annulation    VARCHAR(255),
    cree_le             TIMESTAMPTZ,
    cree_par            VARCHAR(100),
    modifie_le          TIMESTAMPTZ,
    modifie_par         VARCHAR(100),
    version             INTEGER,
    CONSTRAINT uk_pdv_depense_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_depense_montant CHECK (montant > 0),
    CONSTRAINT ck_pdv_depense_statut CHECK (statut IN ('VALIDE', 'ANNULEE')),
    CONSTRAINT ck_pdv_depense_mode CHECK (mode IN ('CAISSE', 'ESPECES', 'BANQUE', 'MOBILE_MONEY'))
);
CREATE INDEX idx_pdv_depense_date ON pdv_depense (entreprise_id, date_depense DESC);
CREATE INDEX idx_pdv_depense_categorie ON pdv_depense (categorie_id);
CREATE INDEX idx_pdv_depense_recurrente ON pdv_depense (recurrente_id, periode);
