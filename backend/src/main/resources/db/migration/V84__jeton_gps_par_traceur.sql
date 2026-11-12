-- Jeton propre a chaque traceur (boitier ou telephone) : il n'envoie des positions
-- que pour son vehicule. Le jeton de l'entreprise (gps_jeton) reste reserve a une
-- passerelle Traccar que l'entreprise controle.
create table gps_jeton_traceur (
    camion_id      bigint primary key references camion (id) on delete cascade,
    entreprise_id  bigint not null references entreprise (id),
    empreinte      varchar(64) not null unique,
    cree_le        timestamptz not null,
    dernier_usage  timestamptz
);

create index ix_gps_jeton_traceur_entreprise on gps_jeton_traceur (entreprise_id);
