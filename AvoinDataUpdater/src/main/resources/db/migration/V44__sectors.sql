drop table stop_sector_queue_item;

create table stop_sector_queue_item (
    id                  bigint      unsigned auto_increment primary key,
    departure_date      date        not null,
    train_number        bigint      not null,
    created             datetime    not null,
    source              varchar(12) not null
);

create index idx_ssqi_created on stop_sector_queue_item(created);