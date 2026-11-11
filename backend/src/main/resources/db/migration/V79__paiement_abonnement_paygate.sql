-- Paiement des abonnements en ligne par PayGate Global (Flooz, T-Money).
-- Le paiement est identifie chez PayGate par notre identifiant (aleatoire, unique) ;
-- il n'est valide qu'apres verification aupres de PayGate, jamais sur la seule foi du rappel.
ALTER TABLE abo_paiement ADD COLUMN paygate_identifiant VARCHAR(40);
ALTER TABLE abo_paiement ADD COLUMN paygate_reseau VARCHAR(10);
ALTER TABLE abo_paiement ADD COLUMN paygate_telephone VARCHAR(20);
ALTER TABLE abo_paiement ADD COLUMN paygate_tx_reference VARCHAR(64);
ALTER TABLE abo_paiement ADD COLUMN paygate_reference_paiement VARCHAR(64);
ALTER TABLE abo_paiement ADD COLUMN paygate_verifie_le TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_abo_paiement_paygate ON abo_paiement (paygate_identifiant)
    WHERE paygate_identifiant IS NOT NULL;
ALTER TABLE abo_paiement ADD CONSTRAINT ck_abo_paiement_paygate_reseau
    CHECK (paygate_reseau IS NULL OR paygate_reseau IN ('FLOOZ', 'TMONEY'));
