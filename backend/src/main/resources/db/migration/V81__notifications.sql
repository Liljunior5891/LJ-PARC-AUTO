-- Notifications envoyees par e-mail, SMS ou WhatsApp. Chaque message est
-- enregistre avant l'envoi : il est renvoye en cas d'echec, et visible dans la
-- console du super-administrateur (en simulation, c'est la qu'on le lit).
CREATE TABLE notification_envoi (
    id             BIGSERIAL PRIMARY KEY,
    -- Nulle pour un message de la plateforme (inscription, compte du super-administrateur).
    entreprise_id  BIGINT REFERENCES entreprise (id),
    canal          VARCHAR(10)   NOT NULL,
    destinataire   VARCHAR(160)  NOT NULL,
    sujet          VARCHAR(200),
    texte          TEXT          NOT NULL,
    -- Raison de l'envoi (VERIFICATION_EMAIL, RAPPEL_ECHEANCE, RECU_PAIEMENT...).
    motif          VARCHAR(40)   NOT NULL,
    -- EN_ATTENTE, ENVOYE, SIMULE (aucun fournisseur branche), ECHEC (renvoye), ABANDONNE.
    statut         VARCHAR(12)   NOT NULL DEFAULT 'EN_ATTENTE',
    fournisseur    VARCHAR(30),
    tentatives     INTEGER       NOT NULL DEFAULT 0,
    erreur         VARCHAR(300),
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    envoye_le      TIMESTAMPTZ,
    CONSTRAINT ck_notification_canal CHECK (canal IN ('EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT ck_notification_statut CHECK (statut IN ('EN_ATTENTE', 'ENVOYE', 'SIMULE', 'ECHEC', 'ABANDONNE'))
);
CREATE INDEX ix_notification_statut ON notification_envoi (statut, cree_le);
CREATE INDEX ix_notification_entreprise ON notification_envoi (entreprise_id, cree_le DESC);
-- Un rappel d'echeance n'est envoye qu'une fois par entreprise, motif et jour.
CREATE INDEX ix_notification_motif ON notification_envoi (entreprise_id, motif, cree_le);
