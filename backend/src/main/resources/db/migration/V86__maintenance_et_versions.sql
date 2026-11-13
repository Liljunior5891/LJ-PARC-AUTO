-- Maintenance programmee par le super-admin, module par module, et notes de version.

CREATE TABLE plateforme_maintenance (
    id          BIGSERIAL PRIMARY KEY,
    message     VARCHAR(500) NOT NULL,
    -- Le bandeau d'annonce s'affiche a partir de annonce_le, le blocage de debut a fin_prevue.
    annonce_le  TIMESTAMPTZ  NOT NULL,
    debut       TIMESTAMPTZ  NOT NULL,
    fin_prevue  TIMESTAMPTZ  NOT NULL,
    -- Fin anticipee (« terminer maintenant ») ou annulation avant le debut.
    terminee_le TIMESTAMPTZ,
    annulee_le  TIMESTAMPTZ,
    cree_le     TIMESTAMPTZ,
    cree_par    VARCHAR(100),
    modifie_le  TIMESTAMPTZ,
    modifie_par VARCHAR(100),
    version     INTEGER,
    CONSTRAINT ck_maintenance_dates CHECK (annonce_le <= debut AND debut < fin_prevue)
);

CREATE INDEX ix_maintenance_fin ON plateforme_maintenance (fin_prevue);

CREATE TABLE plateforme_maintenance_module (
    maintenance_id BIGINT      NOT NULL REFERENCES plateforme_maintenance (id) ON DELETE CASCADE,
    module         VARCHAR(40) NOT NULL,
    PRIMARY KEY (maintenance_id, module)
);

CREATE TABLE plateforme_version (
    id            BIGSERIAL PRIMARY KEY,
    numero        VARCHAR(30)  NOT NULL,
    titre         VARCHAR(150) NOT NULL,
    notes         TEXT         NOT NULL,
    -- Les navigateurs ouverts sur une version plus ancienne sont invites a recharger.
    rechargement  BOOLEAN      NOT NULL DEFAULT TRUE,
    publiee_le    TIMESTAMPTZ  NOT NULL,
    cree_le       TIMESTAMPTZ,
    cree_par      VARCHAR(100),
    modifie_le    TIMESTAMPTZ,
    modifie_par   VARCHAR(100),
    version       INTEGER,
    CONSTRAINT uq_plateforme_version_numero UNIQUE (numero)
);

CREATE INDEX ix_plateforme_version_publiee ON plateforme_version (publiee_le DESC);
