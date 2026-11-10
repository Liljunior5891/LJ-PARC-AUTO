-- Abonnements SaaS : formules (prix mensuel, nombre de modules inclus, limites),
-- modules en option payants, essai gratuit, paiements declares par les
-- entreprises puis valides par le super-administrateur, factures, jours de grace
-- puis lecture seule a l'expiration.

CREATE TABLE abo_parametres (
    id                    BIGINT PRIMARY KEY,
    devise                VARCHAR(10)   NOT NULL DEFAULT 'FCFA',
    duree_essai_jours     INTEGER       NOT NULL DEFAULT 14,
    jours_grace           INTEGER       NOT NULL DEFAULT 7,
    -- Le rappel de renouvellement s'affiche ce nombre de jours avant l'echeance.
    jours_rappel          INTEGER       NOT NULL DEFAULT 7,
    remise_trimestrielle  NUMERIC(5, 2) NOT NULL DEFAULT 0,
    remise_semestrielle   NUMERIC(5, 2) NOT NULL DEFAULT 5,
    remise_annuelle       NUMERIC(5, 2) NOT NULL DEFAULT 10,
    -- Numeros mobile money, compte bancaire... affiches a l'entreprise qui paie.
    instructions_paiement TEXT,
    -- Coordonnees de l'exploitant imprimees sur les factures.
    mentions_facture      TEXT,
    cree_le               TIMESTAMPTZ,
    cree_par              VARCHAR(100),
    modifie_le            TIMESTAMPTZ,
    modifie_par           VARCHAR(100),
    version               INTEGER,
    CONSTRAINT ck_abo_parametres_unique CHECK (id = 1),
    CONSTRAINT ck_abo_parametres_jours CHECK (duree_essai_jours >= 0 AND jours_grace >= 0 AND jours_rappel >= 0)
);

CREATE TABLE abo_formule (
    id               BIGSERIAL PRIMARY KEY,
    nom              VARCHAR(60)    NOT NULL,
    description      VARCHAR(300),
    prix_mensuel     NUMERIC(14, 2) NOT NULL,
    -- Modules au choix compris dans le prix ; nul = tous les modules.
    modules_inclus   INTEGER,
    -- Limites ; nulle = illimite.
    max_utilisateurs INTEGER,
    max_vehicules    INTEGER,
    max_agences      INTEGER,
    max_caisses      INTEGER,
    max_employes     INTEGER,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    mise_en_avant    BOOLEAN        NOT NULL DEFAULT FALSE,
    ordre            INTEGER        NOT NULL DEFAULT 0,
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_abo_formule_nom UNIQUE (nom),
    CONSTRAINT ck_abo_formule_prix CHECK (prix_mensuel >= 0)
);

-- Prix mensuel d'un module pris en plus des modules compris dans la formule.
CREATE TABLE abo_tarif_module (
    id           BIGSERIAL PRIMARY KEY,
    module       VARCHAR(40)    NOT NULL,
    prix_mensuel NUMERIC(14, 2) NOT NULL DEFAULT 0,
    cree_le      TIMESTAMPTZ,
    cree_par     VARCHAR(100),
    modifie_le   TIMESTAMPTZ,
    modifie_par  VARCHAR(100),
    version      INTEGER,
    CONSTRAINT uk_abo_tarif_module UNIQUE (module),
    CONSTRAINT ck_abo_tarif_prix CHECK (prix_mensuel >= 0)
);

-- Etat courant de l'abonnement de chaque entreprise ; une ligne par entreprise.
CREATE TABLE abo_abonnement (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT      NOT NULL REFERENCES entreprise (id),
    -- Nulle pendant l'essai : aucune limite.
    formule_id    BIGINT REFERENCES abo_formule (id),
    periodicite   VARCHAR(20),
    essai         BOOLEAN     NOT NULL DEFAULT TRUE,
    debut         DATE        NOT NULL,
    -- Dernier jour couvert ; la grace commence le lendemain.
    fin           DATE        NOT NULL,
    -- Modules payes (compris + options), separes par des virgules ; nul pendant l'essai.
    modules       VARCHAR(300),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_abo_abonnement_entreprise UNIQUE (entreprise_id)
);

CREATE SEQUENCE abo_facture_seq;

CREATE TABLE abo_paiement (
    id                BIGSERIAL PRIMARY KEY,
    entreprise_id     BIGINT         NOT NULL REFERENCES entreprise (id),
    formule_id        BIGINT         NOT NULL REFERENCES abo_formule (id),
    formule_nom       VARCHAR(60)    NOT NULL,
    periodicite       VARCHAR(20)    NOT NULL,
    modules           VARCHAR(300)   NOT NULL,
    -- Modules factures en option, en plus de ceux compris dans la formule.
    options           VARCHAR(300),
    montant           NUMERIC(14, 2) NOT NULL,
    devise            VARCHAR(10)    NOT NULL,
    moyen             VARCHAR(20)    NOT NULL,
    reference         VARCHAR(100),
    note              VARCHAR(500),
    preuve_chemin     VARCHAR(300),
    preuve_nom        VARCHAR(200),
    preuve_type       VARCHAR(100),
    -- EN_ATTENTE, VALIDE, REJETE
    statut            VARCHAR(20)    NOT NULL,
    motif_rejet       VARCHAR(300),
    -- Saisi directement par le super-administrateur (deja valide).
    saisi_plateforme  BOOLEAN        NOT NULL DEFAULT FALSE,
    traite_par        VARCHAR(100),
    traite_le         TIMESTAMPTZ,
    periode_debut     DATE,
    periode_fin       DATE,
    numero_facture    VARCHAR(30),
    cree_le           TIMESTAMPTZ,
    cree_par          VARCHAR(100),
    modifie_le        TIMESTAMPTZ,
    modifie_par       VARCHAR(100),
    version           INTEGER,
    CONSTRAINT uk_abo_paiement_facture UNIQUE (numero_facture),
    CONSTRAINT ck_abo_paiement_statut CHECK (statut IN ('EN_ATTENTE', 'VALIDE', 'REJETE')),
    CONSTRAINT ck_abo_paiement_montant CHECK (montant >= 0)
);
CREATE INDEX ix_abo_paiement_entreprise ON abo_paiement (entreprise_id);
CREATE INDEX ix_abo_paiement_statut ON abo_paiement (statut);

-- Valeurs de depart, que le super-administrateur ajuste dans sa console.
INSERT INTO abo_parametres (id, instructions_paiement, cree_le, version)
VALUES (1, 'Indiquez ici vos numeros Mobile Money et vos coordonnees bancaires.', now(), 0);

INSERT INTO abo_formule (nom, description, prix_mensuel, modules_inclus, max_utilisateurs, max_vehicules,
                         max_agences, max_caisses, max_employes, mise_en_avant, ordre, cree_le, version)
VALUES ('Essentiel', 'Pour demarrer : un module au choix et une petite equipe.', 10000, 1, 3, 5, 1, 1, 10, FALSE, 1, now(), 0),
       ('Pro', 'Deux modules au choix et des limites confortables.', 25000, 2, 10, 25, 3, 3, 50, TRUE, 2, now(), 0),
       ('Premium', 'Tous les modules, sans limite.', 50000, NULL, NULL, NULL, NULL, NULL, NULL, FALSE, 3, now(), 0);

INSERT INTO abo_tarif_module (module, prix_mensuel, cree_le, version)
VALUES ('GESTION_PARC', 10000, now(), 0),
       ('POINT_DE_VENTE', 10000, now(), 0),
       ('MARKETPLACE', 5000, now(), 0),
       ('AGENCES', 10000, now(), 0),
       ('PRESENCE', 5000, now(), 0);

-- Les entreprises deja inscrites recoivent un essai de 30 jours a compter d'aujourd'hui.
INSERT INTO abo_abonnement (entreprise_id, essai, debut, fin, cree_le, version)
SELECT id, TRUE, CURRENT_DATE, CURRENT_DATE + 29, now(), 0 FROM entreprise;
