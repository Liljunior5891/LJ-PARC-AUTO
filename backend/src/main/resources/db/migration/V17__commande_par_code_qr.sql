-- =============================================================================
-- V17 : commande par le client au moyen d'un code QR pose sur la table.
--
-- Chaque table recoit un jeton imprevisible, imprime dans son code QR : il
-- suffit a retrouver l'etablissement et la table sans compte client. Les
-- articles commandes par le client attendent, selon le reglage de
-- l'etablissement, la validation d'un serveur (statut PROPOSEE) ou partent
-- directement en preparation. Le client peut aussi appeler un serveur ou
-- demander l'addition.
-- =============================================================================

ALTER TABLE pdv_table ADD COLUMN jeton_qr VARCHAR(40);
UPDATE pdv_table SET jeton_qr = md5(random()::text || id::text || clock_timestamp()::text);
ALTER TABLE pdv_table ALTER COLUMN jeton_qr SET NOT NULL;
-- Unique sur toute la plateforme : le jeton seul designe la table.
CREATE UNIQUE INDEX uk_pdv_table_jeton_qr ON pdv_table (jeton_qr);

-- Une commande ouverte par un client n'a pas encore de serveur.
ALTER TABLE pdv_commande ALTER COLUMN serveur_id DROP NOT NULL;
ALTER TABLE pdv_commande ADD COLUMN appel_le TIMESTAMPTZ;
ALTER TABLE pdv_commande ADD COLUMN appel_motif VARCHAR(20);
ALTER TABLE pdv_commande
    ADD CONSTRAINT ck_pdv_commande_appel CHECK (appel_motif IS NULL OR appel_motif IN ('SERVEUR', 'ADDITION'));

ALTER TABLE pdv_ligne_commande ADD COLUMN origine VARCHAR(20) NOT NULL DEFAULT 'SERVEUR';
ALTER TABLE pdv_ligne_commande
    ADD CONSTRAINT ck_pdv_ligne_commande_origine CHECK (origine IN ('SERVEUR', 'CLIENT'));
ALTER TABLE pdv_ligne_commande DROP CONSTRAINT ck_pdv_ligne_commande_statut;
ALTER TABLE pdv_ligne_commande
    ADD CONSTRAINT ck_pdv_ligne_commande_statut
        CHECK (statut IN ('PROPOSEE', 'BROUILLON', 'A_PREPARER', 'EN_PREPARATION', 'PRETE', 'SERVIE', 'ANNULEE'));

-- Reglages du mode restaurant, une ligne par etablissement.
CREATE TABLE pdv_reglage_restauration (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    qr_actif       BOOLEAN NOT NULL DEFAULT TRUE,
    -- Par defaut, un serveur valide la commande du client avant la cuisine.
    qr_validation  BOOLEAN NOT NULL DEFAULT TRUE,
    message_accueil VARCHAR(255),
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_pdv_reglage_restauration UNIQUE (entreprise_id)
);
