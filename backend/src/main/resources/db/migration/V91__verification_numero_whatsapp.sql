-- Numero WhatsApp des notifications confirme par un code (audit securite du 2026-11-14, M-WA-3) :
-- aucune notification ne part vers un numero que l'entreprise n'a pas prouve recevoir.
CREATE TABLE notif_verification_whatsapp (
    entreprise_id BIGINT PRIMARY KEY REFERENCES entreprise (id) ON DELETE CASCADE,
    -- Numero confirme (normalise, +228...) ; nul tant qu'aucun code n'a ete valide.
    numero_verifie VARCHAR(20),
    verifie_le TIMESTAMPTZ,
    verifie_par VARCHAR(150),
    -- Code en cours : empreinte seulement, valable 10 minutes, 5 essais.
    numero_en_cours VARCHAR(20),
    code_empreinte VARCHAR(64),
    code_expire_le TIMESTAMPTZ,
    essais INTEGER NOT NULL DEFAULT 0
);
