create table if not exists stop_sector (
    id                  bigint          unsigned auto_increment primary key,
    station_short_code  varchar(8)      not null,
    commercial_track    varchar(4)      not null,

    train_type          varchar(4),
    locomotive_type     varchar(4),

    direction_south     boolean         not null,
    wagon_count         tinyint         not null,
    stop_sector         char(2)         not null,
    constraint type_check check
        ((train_type is null and locomotive_type is not null) or (train_type is not null and locomotive_type is null))
);

create table if not exists stop_sector_direction (
    station_from        varchar(8)      not null,
    station_to          varchar(8)      not null,
    south               boolean         not null
);

alter table time_table_row add column stop_sector char(2);

create table if not exists stop_sector_queue_item (
    departure_date      date        not null,
    train_number        bigint      not null,
    created             datetime    not null,
    source              varchar(12) not null,
    primary key (departure_date, train_number)
);