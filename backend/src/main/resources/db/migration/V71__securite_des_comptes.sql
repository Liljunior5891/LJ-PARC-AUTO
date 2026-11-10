-- Version des jetons du compte, portee par chaque jeton : elle augmente au
-- changement de mot de passe, a la reinitialisation par l'administrateur ou au
-- changement d'adresse, et les jetons d'une version precedente ne valent plus.
ALTER TABLE utilisateur ADD COLUMN version_jetons INTEGER NOT NULL DEFAULT 0;

-- Mot de passe provisoire (administrateur de la plateforme cree avec celui par
-- defaut) : rien d'autre n'est permis avant de l'avoir change.
ALTER TABLE utilisateur ADD COLUMN mot_de_passe_a_changer BOOLEAN NOT NULL DEFAULT FALSE;

-- Une adresse ne sert qu'une fois, quelles que soient les majuscules.
CREATE UNIQUE INDEX ux_utilisateur_email_minuscules ON utilisateur (lower(email));
