-- Notifications WhatsApp par WAHA (WhatsApp HTTP API, auto-heberge) et choix,
-- par l'administrateur de chaque entreprise, des notifications qu'il recoit.

-- Reglage de la plateforme : une seule ligne, modifiee par le super-administrateur.
CREATE TABLE plateforme_whatsapp (
    id          SMALLINT     PRIMARY KEY DEFAULT 1,
    -- Faux : les messages WhatsApp restent simules (lus dans la console Notifications).
    actif       BOOLEAN      NOT NULL DEFAULT FALSE,
    url         VARCHAR(200) NOT NULL DEFAULT 'http://127.0.0.1:3005',
    -- Cle d'API de WAHA (en-tete X-Api-Key) ; jamais renvoyee a l'ecran.
    cle_api     VARCHAR(200),
    -- WAHA Core ne gere qu'une session, nommee « default ».
    session     VARCHAR(60)  NOT NULL DEFAULT 'default',
    -- Pause entre deux messages, pour ne pas etre pris pour un robot par WhatsApp.
    pause_ms    INTEGER      NOT NULL DEFAULT 1500,
    modifie_le  TIMESTAMPTZ,
    modifie_par VARCHAR(160),
    CONSTRAINT ck_plateforme_whatsapp_unique CHECK (id = 1),
    CONSTRAINT ck_plateforme_whatsapp_pause CHECK (pause_ms BETWEEN 0 AND 60000)
);
INSERT INTO plateforme_whatsapp (id) VALUES (1);

-- Choix de l'administrateur, par type de notification et par canal.
-- Sans ligne, le reglage par defaut du type s'applique.
CREATE TABLE notif_preference (
    entreprise_id BIGINT       NOT NULL REFERENCES entreprise (id),
    type          VARCHAR(40)  NOT NULL,
    whatsapp      BOOLEAN      NOT NULL,
    email         BOOLEAN      NOT NULL,
    modifie_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modifie_par   VARCHAR(160),
    PRIMARY KEY (entreprise_id, type)
);

-- Alertes deja envoyees (une cle par evenement) : une alerte n'est jamais envoyee deux fois.
CREATE TABLE notif_alerte_envoyee (
    entreprise_id BIGINT       NOT NULL REFERENCES entreprise (id),
    cle           VARCHAR(120) NOT NULL,
    envoyee_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (entreprise_id, cle)
);
CREATE INDEX ix_notif_alerte_date ON notif_alerte_envoyee (envoyee_le);
