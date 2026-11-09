-- Journal d'audit de tous les modules (hors Agences, qui a le sien) : creations,
-- modifications et suppressions de donnees, connexions. La base refuse toute
-- modification ou suppression d'une ligne du journal.
CREATE TABLE journal_activite (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT       NOT NULL REFERENCES entreprise (id),
    date_action   TIMESTAMPTZ  NOT NULL,
    auteur_id     BIGINT,
    auteur_nom    VARCHAR(150),
    -- PARC, EXPLOITATION, FINANCES, MAINTENANCE, GPS, POINT_DE_VENTE, MARKETPLACE, ADMINISTRATION, CONNEXION
    module        VARCHAR(30)  NOT NULL,
    -- CREATION, MODIFICATION, SUPPRESSION, CONNEXION, ECHEC_CONNEXION
    action        VARCHAR(30)  NOT NULL,
    objet         VARCHAR(60)  NOT NULL,
    objet_libelle VARCHAR(80)  NOT NULL,
    objet_id      BIGINT,
    description   VARCHAR(500) NOT NULL,
    avant         TEXT,
    apres         TEXT,
    adresse_ip    VARCHAR(60),
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER
);
CREATE INDEX ix_journal_activite_date ON journal_activite (entreprise_id, date_action DESC);
CREATE INDEX ix_journal_activite_module ON journal_activite (entreprise_id, module, date_action DESC);
CREATE INDEX ix_journal_activite_objet ON journal_activite (entreprise_id, objet, objet_id);

CREATE OR REPLACE FUNCTION journal_activite_immuable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Le journal d''audit ne se modifie pas et ne se supprime pas';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_journal_activite_immuable
    BEFORE UPDATE OR DELETE ON journal_activite
    FOR EACH ROW EXECUTE FUNCTION journal_activite_immuable();
