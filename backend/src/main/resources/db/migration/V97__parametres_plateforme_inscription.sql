-- =============================================================================
-- V97 : reglages generaux de la plateforme, modifies par le super-administrateur.
--
-- Premier reglage : la confirmation par e-mail de l'inscription en libre-service.
-- Active (par defaut, comme avant) : la demande attend que le lien recu par
-- e-mail soit ouvert. Desactive : le compte est cree tout de suite, utile tant
-- qu'aucun serveur d'e-mail n'est branche.
-- =============================================================================

create table plateforme_parametres (
    id                              smallint primary key check (id = 1),
    inscription_confirmation_email  boolean not null default true,
    modifie_le                      timestamptz,
    modifie_par                     varchar(150)
);

insert into plateforme_parametres (id) values (1);
