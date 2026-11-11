-- Journal d'audit general du super-admin : les actions de la plateforme elle-meme
-- (formules, reglages et paiements d'abonnement, comptes et connexions du
-- super-admin) n'appartiennent a aucune entreprise.
ALTER TABLE journal_activite ALTER COLUMN entreprise_id DROP NOT NULL;

-- Lecture de toutes les entreprises a la fois, par date.
CREATE INDEX ix_journal_activite_date_globale ON journal_activite (date_action DESC);
CREATE INDEX ix_agc_audit_date_globale ON agc_journal_audit (date_action DESC);
