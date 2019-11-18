CREATE TABLE track_work_notification (
  id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  ruma_id INT UNSIGNED NOT NULL,
  ruma_version INT UNSIGNED NOT NULL,
  state TINYINT UNSIGNED NOT NULL,
  organization VARCHAR(64) NOT NULL,
  created DATETIME NOT NULL,
  modified DATETIME NULL,
  traffic_safety_plan BIT NOT NULL,
  speed_limit_removal_plan BIT NOT NULL,
  electricity_safety_plan BIT NOT NULL,
  speed_limit_plan BIT NOT NULL,
  person_in_charge_plan BIT NOT NULL,
  PRIMARY KEY (id));

create table track_work_part
(
    id bigint unsigned not null auto_increment
        primary key,
    part_index int unsigned not null,
    permission_minimum_duration varchar(64) not null,
    start_day datetime not null,
    planned_working_gap time null,
    advance_notifications varchar(4000) not null,
    contains_fire_work bit not null,
    track_work_notification_id bigint unsigned not null,
    constraint FK_track_work_notification_id
        foreign key (track_work_notification_id) references track_work_notification (id)
            on update cascade on delete cascade
);
create index FK_track_work_notification_id_idx
    on track_work_part (track_work_notification_id);

create table ruma_location
(
    id bigint unsigned not null auto_increment
        primary key,
    location_type varchar(10) not null,
    operating_point_id varchar(64),
    section_between_operating_points_id varchar(64),
    track_work_part_id bigint unsigned not null,
    constraint FK_track_work_part_id
        foreign key (track_work_part_id) references track_work_part (id)
            on update cascade on delete cascade
);
create index FK_track_work_part_id_idx
    on ruma_location (track_work_part_id);

create table identifier_range
(
    id bigint unsigned not null auto_increment
        primary key,
    element_id varchar(64),
    element_pair_id1 varchar(64),
    element_pair_id2 varchar(64),
    speed int unsigned,
    signs bit,
    balises bit,
    ruma_location_id bigint unsigned not null,
    constraint FK_ruma_location_id
        foreign key (ruma_location_id) references ruma_location (id)
            on update cascade on delete cascade
);
create index FK_ruma_location_id_idx
    on identifier_range (ruma_location_id);
