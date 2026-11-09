-- Gestion de presence : les employes pointent leur arrivee en scannant le code QR
-- de leur lieu de travail (il change toutes les 30 secondes, affiche par une borne)
-- puis en tapant leur PIN personnel, depuis leur telephone (position GPS controlee)
-- ou directement sur la tablette du lieu.

CREATE TABLE pres_lieu (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    nom            VARCHAR(80) NOT NULL,
    adresse        VARCHAR(255),
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    rayon_metres   INTEGER NOT NULL DEFAULT 100,
    -- Identifiant public du lieu, porte par l'adresse du code QR.
    jeton_public   VARCHAR(32) NOT NULL,
    -- Secret qui fait changer le code QR toutes les 30 secondes ; ne sort jamais du serveur.
    secret_qr      VARCHAR(64) NOT NULL,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_pres_lieu_nom UNIQUE (entreprise_id, nom),
    CONSTRAINT uk_pres_lieu_jeton UNIQUE (jeton_public),
    CONSTRAINT ck_pres_lieu_rayon CHECK (rayon_metres BETWEEN 10 AND 5000)
);

-- Gestionnaire de parc ou superviseur d'agence responsable du lieu : il n'en voit que les employes.
CREATE TABLE pres_lieu_responsable (
    lieu_id        BIGINT NOT NULL REFERENCES pres_lieu (id) ON DELETE CASCADE,
    utilisateur_id BIGINT NOT NULL,
    PRIMARY KEY (lieu_id, utilisateur_id)
);

CREATE TABLE pres_employe (
    id             BIGSERIAL PRIMARY KEY,
    entreprise_id  BIGINT NOT NULL REFERENCES entreprise (id),
    matricule      VARCHAR(20) NOT NULL,
    nom            VARCHAR(80) NOT NULL,
    prenom         VARCHAR(80),
    telephone      VARCHAR(30),
    email          VARCHAR(150),
    fonction       VARCHAR(80),
    service        VARCHAR(80),
    date_embauche  DATE,
    -- Compte utilisateur converti en employe ; nul pour un employe sans compte.
    utilisateur_id BIGINT,
    -- Empreinte (HMAC) du PIN : unique dans l'entreprise, le PIN n'est jamais stocke en clair.
    pin_empreinte  VARCHAR(64) NOT NULL,
    actif          BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le        TIMESTAMPTZ,
    cree_par       VARCHAR(100),
    modifie_le     TIMESTAMPTZ,
    modifie_par    VARCHAR(100),
    version        INTEGER,
    CONSTRAINT uk_pres_employe_matricule UNIQUE (entreprise_id, matricule),
    CONSTRAINT uk_pres_employe_pin UNIQUE (entreprise_id, pin_empreinte)
);
CREATE UNIQUE INDEX uk_pres_employe_utilisateur ON pres_employe (entreprise_id, utilisateur_id) WHERE utilisateur_id IS NOT NULL;

-- Lieux ou l'employe peut pointer.
CREATE TABLE pres_employe_lieu (
    employe_id BIGINT NOT NULL REFERENCES pres_employe (id) ON DELETE CASCADE,
    lieu_id    BIGINT NOT NULL REFERENCES pres_lieu (id) ON DELETE CASCADE,
    PRIMARY KEY (employe_id, lieu_id)
);

-- Tablette ou ecran pose sur un lieu : affiche le code QR et un clavier PIN.
CREATE TABLE pres_borne (
    id                 BIGSERIAL PRIMARY KEY,
    entreprise_id      BIGINT NOT NULL REFERENCES entreprise (id),
    lieu_id            BIGINT NOT NULL REFERENCES pres_lieu (id) ON DELETE CASCADE,
    nom                VARCHAR(80) NOT NULL,
    -- Empreinte (SHA-256) du jeton remis a la tablette a l'appairage.
    jeton_empreinte    VARCHAR(64),
    code_appairage     VARCHAR(6),
    code_expire_le     TIMESTAMPTZ,
    appairee_le        TIMESTAMPTZ,
    dernier_contact    TIMESTAMPTZ,
    actif              BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER,
    CONSTRAINT uk_pres_borne_jeton UNIQUE (jeton_empreinte)
);
CREATE UNIQUE INDEX uk_pres_borne_code ON pres_borne (code_appairage) WHERE code_appairage IS NOT NULL;

-- Horaire de l'entreprise : une ligne par entreprise.
CREATE TABLE pres_reglage (
    id                 BIGSERIAL PRIMARY KEY,
    entreprise_id      BIGINT NOT NULL REFERENCES entreprise (id),
    heure_arrivee      TIME NOT NULL DEFAULT '08:00',
    tolerance_minutes  INTEGER NOT NULL DEFAULT 10,
    -- Jours travailles, 1 = lundi ... 7 = dimanche.
    jours_travailles   VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6',
    -- Passe cette heure, un employe attendu qui n'a pas pointe est absent.
    heure_limite       TIME NOT NULL DEFAULT '12:00',
    cree_le            TIMESTAMPTZ,
    cree_par           VARCHAR(100),
    modifie_le         TIMESTAMPTZ,
    modifie_par        VARCHAR(100),
    version            INTEGER,
    CONSTRAINT uk_pres_reglage UNIQUE (entreprise_id),
    CONSTRAINT ck_pres_reglage_tolerance CHECK (tolerance_minutes BETWEEN 0 AND 240)
);

-- Arrivee d'un employe : une par jour.
CREATE TABLE pres_pointage (
    id               BIGSERIAL PRIMARY KEY,
    entreprise_id    BIGINT NOT NULL REFERENCES entreprise (id),
    employe_id       BIGINT NOT NULL REFERENCES pres_employe (id) ON DELETE CASCADE,
    jour             DATE NOT NULL,
    arrivee_le       TIMESTAMPTZ NOT NULL,
    lieu_id          BIGINT REFERENCES pres_lieu (id) ON DELETE SET NULL,
    mode             VARCHAR(20) NOT NULL,
    distance_metres  INTEGER,
    precision_metres INTEGER,
    retard_minutes   INTEGER NOT NULL DEFAULT 0,
    -- Arrive apres l'heure limite : accepte, compte comme un gros retard.
    apres_limite     BOOLEAN NOT NULL DEFAULT FALSE,
    saisi_par        VARCHAR(150),
    motif            VARCHAR(255),
    cree_le          TIMESTAMPTZ,
    cree_par         VARCHAR(100),
    modifie_le       TIMESTAMPTZ,
    modifie_par      VARCHAR(100),
    version          INTEGER,
    CONSTRAINT uk_pres_pointage_jour UNIQUE (employe_id, jour),
    CONSTRAINT ck_pres_pointage_mode CHECK (mode IN ('TELEPHONE', 'BORNE', 'MANUEL'))
);
CREATE INDEX idx_pres_pointage_jour ON pres_pointage (entreprise_id, jour);
