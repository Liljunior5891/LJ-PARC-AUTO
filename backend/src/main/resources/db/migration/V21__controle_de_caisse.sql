-- =============================================================================
-- V21 : controle de caisse.
--
-- Le gerant (ou l'administrateur) ouvre et ferme la session d'une caisse ;
-- tout le personnel affecte a la caisse encaisse ensuite sur cette session.
-- Le fond de caisse et l'argent compte a la fermeture se saisissent par
-- coupure (billettage). Les entrees et sorties d'especes hors vente (apport de
-- monnaie, depense, retrait) sont tracees avec leur motif. Une commande de
-- table est rattachee a la session sur laquelle elle sera reglee.
-- =============================================================================

-- Un gerant peut tenir ouvertes plusieurs caisses a la fois : seule l'unicite
-- par caisse demeure (uk_pdv_session_caisse_ouverte, V18).
DROP INDEX IF EXISTS uk_pdv_session_ouverte;

ALTER TABLE pdv_session_caisse
    ADD COLUMN fermee_par_id       BIGINT REFERENCES utilisateur (id),
    ADD COLUMN fermee_par_nom      VARCHAR(150),
    ADD COLUMN justification_ecart TEXT;

-- Billettage : nombre de billets et de pieces de chaque valeur, compte a
-- l'ouverture et a la fermeture.
CREATE TABLE pdv_billettage (
    session_id BIGINT NOT NULL REFERENCES pdv_session_caisse (id) ON DELETE CASCADE,
    moment     VARCHAR(20) NOT NULL,
    valeur     NUMERIC(12, 2) NOT NULL,
    quantite   INTEGER NOT NULL,
    PRIMARY KEY (session_id, moment, valeur),
    CONSTRAINT ck_pdv_billettage_moment CHECK (moment IN ('OUVERTURE', 'FERMETURE')),
    CONSTRAINT ck_pdv_billettage_quantite CHECK (quantite >= 0)
);

CREATE TABLE pdv_mouvement_caisse (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    session_id     BIGINT NOT NULL REFERENCES pdv_session_caisse (id),
    type           VARCHAR(20) NOT NULL,
    montant        NUMERIC(16, 2) NOT NULL,
    motif          VARCHAR(255) NOT NULL,
    auteur_id      BIGINT NOT NULL REFERENCES utilisateur (id),
    auteur_nom     VARCHAR(150) NOT NULL,
    date_mouvement TIMESTAMPTZ NOT NULL,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT ck_pdv_mouvement_caisse_type CHECK (type IN ('ENTREE', 'SORTIE')),
    CONSTRAINT ck_pdv_mouvement_caisse_montant CHECK (montant > 0)
);
CREATE INDEX idx_pdv_mouvement_caisse_session ON pdv_mouvement_caisse (session_id, date_mouvement);

-- Session sur laquelle la commande sera reglee : sa fermeture attend que la
-- commande soit encaissee ou annulee.
ALTER TABLE pdv_commande ADD COLUMN session_id BIGINT REFERENCES pdv_session_caisse (id);
CREATE INDEX idx_pdv_commande_session ON pdv_commande (session_id) WHERE statut = 'OUVERTE';
