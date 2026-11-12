-- Inscription en libre-service : l'entreprise n'est creee qu'apres le clic sur le
-- lien envoye a l'adresse donnee. En attendant, la demande est gardee ici (mot de
-- passe deja chiffre) ; seule l'empreinte du jeton du lien est conservee.
CREATE TABLE inscription_attente (
    id               BIGSERIAL PRIMARY KEY,
    email            VARCHAR(150) NOT NULL,
    jeton_empreinte  VARCHAR(64)  NOT NULL,
    donnees          TEXT         NOT NULL,
    cree_le          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expire_le        TIMESTAMPTZ  NOT NULL,
    utilisee_le      TIMESTAMPTZ,
    CONSTRAINT uk_inscription_attente_jeton UNIQUE (jeton_empreinte)
);
CREATE INDEX ix_inscription_attente_email ON inscription_attente (lower(email), cree_le);
