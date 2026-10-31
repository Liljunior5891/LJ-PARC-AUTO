-- =============================================================================
-- V15 : point de vente en mode bar / restaurant / cafe.
--
-- L'entreprise choisit le type d'activite de son point de vente (variante du
-- module) : boutique ou restauration. La restauration ajoute les salles et
-- leurs tables, les commandes par table et leurs lignes, suivies de l'envoi en
-- cuisine ou au bar jusqu'au service, puis encaissees en une ou plusieurs fois.
-- =============================================================================

-- Type d'activite des modules qui se declinent.
ALTER TABLE entreprise_module ADD COLUMN variante VARCHAR(40);
UPDATE entreprise_module SET variante = 'BOUTIQUE' WHERE module = 'POINT_DE_VENTE';

-- Ecran qui recoit les articles d'une categorie commandes a table.
ALTER TABLE pdv_categorie ADD COLUMN poste VARCHAR(20) NOT NULL DEFAULT 'AUCUN';
ALTER TABLE pdv_categorie
    ADD CONSTRAINT ck_pdv_categorie_poste CHECK (poste IN ('AUCUN', 'CUISINE', 'BAR'));

CREATE TABLE pdv_salle (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    nom           VARCHAR(80) NOT NULL,
    ordre         INTEGER NOT NULL DEFAULT 0,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_salle_nom UNIQUE (entreprise_id, nom)
);

-- Position et taille en unites d'un plan de 100 x 60, pour un dessin
-- proportionnel quelle que soit la taille de l'ecran.
CREATE TABLE pdv_table (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    salle_id      BIGINT NOT NULL REFERENCES pdv_salle (id),
    nom           VARCHAR(40) NOT NULL,
    places        INTEGER NOT NULL DEFAULT 4,
    forme         VARCHAR(20) NOT NULL DEFAULT 'CARREE',
    pos_x         INTEGER NOT NULL DEFAULT 0,
    pos_y         INTEGER NOT NULL DEFAULT 0,
    largeur       INTEGER NOT NULL DEFAULT 10,
    hauteur       INTEGER NOT NULL DEFAULT 10,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_table_nom UNIQUE (entreprise_id, nom),
    CONSTRAINT ck_pdv_table_forme CHECK (forme IN ('CARREE', 'RONDE', 'RECTANGLE')),
    CONSTRAINT ck_pdv_table_places CHECK (places > 0)
);
CREATE INDEX idx_pdv_table_salle ON pdv_table (salle_id);

CREATE TABLE pdv_commande (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    numero        VARCHAR(40) NOT NULL,
    -- Sans table : commande au comptoir ou a emporter, reperee par son libelle.
    table_id      BIGINT REFERENCES pdv_table (id),
    libelle       VARCHAR(80),
    serveur_id    BIGINT NOT NULL REFERENCES utilisateur (id),
    serveur_nom   VARCHAR(150) NOT NULL,
    couverts      INTEGER,
    statut        VARCHAR(20) NOT NULL,
    ouverte_le    TIMESTAMPTZ NOT NULL,
    fermee_le     TIMESTAMPTZ,
    addition_le   TIMESTAMPTZ,
    envois        INTEGER NOT NULL DEFAULT 0,
    note          VARCHAR(255),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_pdv_commande_numero UNIQUE (entreprise_id, numero),
    CONSTRAINT ck_pdv_commande_statut CHECK (statut IN ('OUVERTE', 'ENCAISSEE', 'ANNULEE'))
);
-- Une table ne porte qu'une commande ouverte a la fois.
CREATE UNIQUE INDEX uk_pdv_commande_table_ouverte ON pdv_commande (table_id) WHERE statut = 'OUVERTE';
CREATE INDEX idx_pdv_commande_entreprise ON pdv_commande (entreprise_id, statut, ouverte_le DESC);

-- Ticket d'une addition : la commande et la table servies.
ALTER TABLE pdv_vente ADD COLUMN commande_id BIGINT REFERENCES pdv_commande (id);
ALTER TABLE pdv_vente ADD COLUMN table_nom VARCHAR(80);
CREATE INDEX idx_pdv_vente_commande ON pdv_vente (commande_id);

CREATE TABLE pdv_ligne_commande (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id),
    commande_id   BIGINT NOT NULL REFERENCES pdv_commande (id) ON DELETE CASCADE,
    article_id    BIGINT NOT NULL REFERENCES pdv_article (id),
    libelle       VARCHAR(150) NOT NULL,
    prix_unitaire NUMERIC(16, 2) NOT NULL,
    prix_achat    NUMERIC(16, 2),
    quantite      NUMERIC(14, 3) NOT NULL,
    note          VARCHAR(255),
    poste         VARCHAR(20) NOT NULL,
    statut        VARCHAR(20) NOT NULL,
    -- Numero d'envoi en preparation : les lignes d'un meme envoi forment un bon.
    envoi         INTEGER,
    envoyee_le    TIMESTAMPTZ,
    prete_le      TIMESTAMPTZ,
    servie_le     TIMESTAMPTZ,
    annulee_le    TIMESTAMPTZ,
    annulee_par   VARCHAR(100),
    vente_id      BIGINT REFERENCES pdv_vente (id),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT ck_pdv_ligne_commande_quantite CHECK (quantite > 0),
    CONSTRAINT ck_pdv_ligne_commande_poste CHECK (poste IN ('AUCUN', 'CUISINE', 'BAR')),
    CONSTRAINT ck_pdv_ligne_commande_statut
        CHECK (statut IN ('BROUILLON', 'A_PREPARER', 'EN_PREPARATION', 'PRETE', 'SERVIE', 'ANNULEE'))
);
CREATE INDEX idx_pdv_ligne_commande ON pdv_ligne_commande (commande_id);
CREATE INDEX idx_pdv_ligne_commande_poste ON pdv_ligne_commande (entreprise_id, poste, statut);

-- Profils de la restauration.
ALTER TABLE utilisateur_role DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;
ALTER TABLE utilisateur_role
    ADD CONSTRAINT ck_utilisateur_role_valeur
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR', 'GERANT', 'CAISSIER',
                        'SERVEUR', 'PREPARATEUR'));
