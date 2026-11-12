-- Double authentification (code a 6 chiffres d'une application d'authentification).
-- Le secret est chiffre (AES-GCM) ; les codes de secours ne sont gardes que haches.
ALTER TABLE utilisateur ADD COLUMN totp_secret VARCHAR(200);
ALTER TABLE utilisateur ADD COLUMN totp_actif BOOLEAN NOT NULL DEFAULT FALSE;
-- Dernier pas de temps accepte : un meme code ne sert pas deux fois.
ALTER TABLE utilisateur ADD COLUMN totp_dernier_pas BIGINT;

CREATE TABLE utilisateur_code_secours (
    id              BIGSERIAL PRIMARY KEY,
    utilisateur_id  BIGINT      NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    empreinte       VARCHAR(100) NOT NULL,
    utilise_le      TIMESTAMPTZ
);
CREATE INDEX ix_code_secours_utilisateur ON utilisateur_code_secours (utilisateur_id);
