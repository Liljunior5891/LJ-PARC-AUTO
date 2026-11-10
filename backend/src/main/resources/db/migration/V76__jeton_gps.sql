-- Jeton des traceurs GPS d'une entreprise : la passerelle (Traccar) ou le
-- boitier envoie ses positions avec lui, sans session d'utilisateur. Seule son
-- empreinte SHA-256 est gardee.
CREATE TABLE gps_jeton (
    id            BIGSERIAL PRIMARY KEY,
    entreprise_id BIGINT      NOT NULL UNIQUE REFERENCES entreprise (id),
    empreinte     VARCHAR(64) NOT NULL UNIQUE,
    cree_le       TIMESTAMPTZ NOT NULL,
    dernier_usage TIMESTAMPTZ
);
