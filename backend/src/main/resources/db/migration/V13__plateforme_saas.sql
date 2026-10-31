-- =============================================================================
-- V13 : passage en plateforme SaaS multi-entreprises.
--
-- Chaque entreprise cree son compte, active les modules dont elle a besoin
-- (gestion de parc, puis point de vente, marketplace...) et ne voit que ses
-- propres donnees. Le cloisonnement repose sur une colonne entreprise_id
-- portee par toutes les tables metier et filtree par Hibernate.
--
-- Une installation existante n'est pas perdue : ses donnees sont rattachees a
-- une premiere entreprise, creee a partir du parametrage societe, et ses
-- comptes en deviennent les utilisateurs.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Entreprises abonnees et modules souscrits
-- -----------------------------------------------------------------------------
CREATE TABLE entreprise (
    id          BIGSERIAL PRIMARY KEY,
    nom         VARCHAR(150) NOT NULL,
    pays        VARCHAR(100),
    telephone   VARCHAR(40),
    email       VARCHAR(150),
    statut      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cree_le     TIMESTAMPTZ,
    cree_par    VARCHAR(100),
    modifie_le  TIMESTAMPTZ,
    modifie_par VARCHAR(100),
    version     INTEGER,
    CONSTRAINT ck_entreprise_statut CHECK (statut IN ('ACTIVE', 'SUSPENDUE'))
);

CREATE TABLE entreprise_module (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT NOT NULL REFERENCES entreprise (id) ON DELETE CASCADE,
    module        VARCHAR(40) NOT NULL,
    actif         BOOLEAN NOT NULL DEFAULT TRUE,
    active_le     TIMESTAMPTZ,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uk_entreprise_module UNIQUE (entreprise_id, module)
);

-- -----------------------------------------------------------------------------
-- Reprise d'une installation existante
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    existante BOOLEAN := FALSE;
    t         TEXT;
    trouve    BOOLEAN;
BEGIN
    -- La moindre ligne dans une table a rattacher suffit : elle devra avoir une entreprise.
    FOREACH t IN ARRAY ARRAY ['utilisateur', 'compteur_numero', 'camion', 'chauffeur', 'affectation',
        'document', 'client', 'location', 'mission', 'livraison', 'facture', 'paiement', 'recette',
        'depense', 'position_gps', 'zone_geofence', 'alerte_gps', 'entretien', 'pneu', 'incident',
        'parametre_societe']
    LOOP
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I)', t) INTO trouve;
        existante := existante OR trouve;
    END LOOP;
    IF existante THEN
        INSERT INTO entreprise (id, nom, pays, telephone, email, statut, cree_le, cree_par)
        SELECT 1,
               COALESCE((SELECT nom FROM parametre_societe LIMIT 1), 'Entreprise principale'),
               (SELECT pays FROM parametre_societe LIMIT 1),
               (SELECT telephone FROM parametre_societe LIMIT 1),
               (SELECT email FROM parametre_societe LIMIT 1),
               'ACTIVE', now(), 'migration';
        INSERT INTO entreprise_module (entreprise_id, module, actif, active_le, cree_le, cree_par)
        VALUES (1, 'GESTION_PARC', TRUE, now(), now(), 'migration');
    END IF;
END $$;

SELECT setval(pg_get_serial_sequence('entreprise', 'id'), GREATEST((SELECT MAX(id) FROM entreprise), 1),
              (SELECT MAX(id) FROM entreprise) IS NOT NULL);

-- -----------------------------------------------------------------------------
-- Rattachement de chaque table metier a une entreprise
-- -----------------------------------------------------------------------------
CREATE FUNCTION rattacher_a_entreprise(t TEXT) RETURNS VOID AS $$
BEGIN
    EXECUTE format('ALTER TABLE %I ADD COLUMN entreprise_id BIGINT', t);
    EXECUTE format('UPDATE %I SET entreprise_id = 1', t);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN entreprise_id SET NOT NULL', t);
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (entreprise_id) REFERENCES entreprise (id)',
                   t, 'fk_' || t || '_entreprise');
    EXECUTE format('CREATE INDEX %I ON %I (entreprise_id)', 'idx_' || t || '_entreprise', t);
END;
$$ LANGUAGE plpgsql;

-- Une reference unique l'est desormais au sein d'une entreprise : deux
-- entreprises peuvent chacune avoir leur facture FAC-2026-0001.
CREATE FUNCTION unique_par_entreprise(t TEXT, c TEXT) RETURNS VOID AS $$
DECLARE
    contrainte TEXT;
BEGIN
    FOR contrainte IN
        SELECT con.conname
        FROM pg_constraint con
                 JOIN pg_class rel ON rel.oid = con.conrelid
                 JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = ANY (con.conkey)
        WHERE rel.relname = t
          AND con.contype = 'u'
          AND att.attname = c
          AND array_length(con.conkey, 1) = 1
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', t, contrainte);
    END LOOP;
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE (entreprise_id, %I)', t, 'uk_' || t || '_' || c, c);
END;
$$ LANGUAGE plpgsql;

SELECT rattacher_a_entreprise(t)
FROM unnest(ARRAY [
    'camion', 'chauffeur', 'affectation', 'document',
    'client', 'location', 'mission', 'livraison',
    'facture', 'paiement', 'recette', 'depense',
    'position_gps', 'zone_geofence', 'alerte_gps',
    'entretien', 'pneu', 'incident',
    'parametre_societe'
    ]) AS t;

SELECT unique_par_entreprise(t, c)
FROM (VALUES ('camion', 'immatriculation'),
             ('chauffeur', 'matricule'),
             ('client', 'code'),
             ('location', 'numero'),
             ('mission', 'numero'),
             ('livraison', 'numero'),
             ('facture', 'numero'),
             ('recette', 'numero'),
             ('depense', 'numero'),
             ('entretien', 'numero'),
             ('pneu', 'reference'),
             ('incident', 'numero')) AS u(t, c);

DROP FUNCTION unique_par_entreprise(TEXT, TEXT);
DROP FUNCTION rattacher_a_entreprise(TEXT);

-- Un traceur n'est rattache qu'a un vehicule de l'entreprise qui l'a declare.
CREATE UNIQUE INDEX uk_camion_traceur ON camion (entreprise_id, gps_device_id) WHERE gps_device_id IS NOT NULL;

-- Le parametrage n'a plus une ligne unique, mais une par entreprise.
DROP INDEX ux_parametre_societe_unique;
ALTER TABLE parametre_societe ADD CONSTRAINT uk_parametre_societe_entreprise UNIQUE (entreprise_id);

-- Chaque entreprise numerote ses pieces a partir de 1.
ALTER TABLE compteur_numero ADD COLUMN entreprise_id BIGINT;
UPDATE compteur_numero SET entreprise_id = 1;
ALTER TABLE compteur_numero ALTER COLUMN entreprise_id SET NOT NULL;
ALTER TABLE compteur_numero DROP CONSTRAINT compteur_numero_pkey;
ALTER TABLE compteur_numero ADD PRIMARY KEY (entreprise_id, prefixe, annee);
ALTER TABLE compteur_numero
    ADD CONSTRAINT fk_compteur_numero_entreprise FOREIGN KEY (entreprise_id) REFERENCES entreprise (id);

-- -----------------------------------------------------------------------------
-- Comptes utilisateurs
-- -----------------------------------------------------------------------------
-- L'adresse email reste unique sur toute la plateforme : elle suffit a se
-- connecter sans avoir a designer son entreprise. Seul l'administrateur de la
-- plateforme n'appartient a aucune entreprise.
ALTER TABLE utilisateur ADD COLUMN entreprise_id BIGINT REFERENCES entreprise (id);
UPDATE utilisateur SET entreprise_id = 1;
CREATE INDEX idx_utilisateur_entreprise ON utilisateur (entreprise_id);

ALTER TABLE utilisateur_role DROP CONSTRAINT IF EXISTS ck_utilisateur_role_valeur;
ALTER TABLE utilisateur_role
    ADD CONSTRAINT ck_utilisateur_role_valeur
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR'));
