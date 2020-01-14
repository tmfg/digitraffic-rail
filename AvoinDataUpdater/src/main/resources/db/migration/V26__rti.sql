CREATE TABLE track_work_notification
(
  id INT UNSIGNED NOT NULL,
  version INT UNSIGNED NOT NULL,
  state TINYINT UNSIGNED NOT NULL,
  organization VARCHAR(64) NOT NULL,
  created DATETIME NOT NULL,
  modified DATETIME NULL,
  traffic_safety_plan BIT NOT NULL,
  speed_limit_removal_plan BIT NOT NULL,
  electricity_safety_plan BIT NOT NULL,
  speed_limit_plan BIT NOT NULL,
  person_in_charge_plan BIT NOT NULL,
  PRIMARY KEY (id, version)
);

CREATE INDEX track_work_notification_modified_id_version_idx ON track_work_notification
(
    modified,
    id asc,
    version asc
);

CREATE TABLE track_work_part
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    part_index INT UNSIGNED NOT NULL,
    permission_minimum_duration VARCHAR(64) NOT NULL,
    start_day DATETIME NOT NULL,
    planned_working_gap TIME NULL,
    advance_notifications VARCHAR(4000),
    contains_fire_work BIT NOT NULL,
    track_work_notification_id INT UNSIGNED NOT NULL,
    track_work_notification_version INT UNSIGNED NOT NULL,
    CONSTRAINT FK_track_work_notification_id
        FOREIGN KEY (track_work_notification_id, track_work_notification_version) REFERENCES track_work_notification (id, version)
            ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX FK_track_work_notification_id_version_idx
    ON track_work_part (track_work_notification_id, track_work_notification_version);

CREATE TABLE ruma_location
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    location_type VARCHAR(10) NOT NULL,
    operating_point_id VARCHAR(64),
    section_between_operating_points_id VARCHAR(64),
    track_work_part_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT FK_track_work_part_id
        FOREIGN KEY (track_work_part_id) REFERENCES track_work_part (id)
            ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX FK_track_work_part_id_idx
    ON ruma_location (track_work_part_id);

CREATE TABLE identifier_range
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    element_id VARCHAR(64),
    element_pair_id1 VARCHAR(64),
    element_pair_id2 VARCHAR(64),
    speed INT UNSIGNED,
    signs BIT,
    balises BIT,
    ruma_location_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT FK_ruma_location_id
        FOREIGN KEY (ruma_location_id) REFERENCES ruma_location (id)
            ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX FK_ruma_location_id_idx
    ON identifier_range (ruma_location_id);

CREATE TABLE element_range
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    element_id1 VARCHAR(64),
    element_id2 VARCHAR(64),
    track_kilometer_range VARCHAR(32),
    track_ids VARCHAR(4000) NOT NULL,
    specifiers VARCHAR(4000),
    identifier_range_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT FK_identifier_Range_id
        FOREIGN KEY (identifier_range_id) REFERENCES identifier_range (id)
            ON UPDATE CASCADE ON DELETE CASCADE
);
CREATE INDEX FK_identifier_range_id_idx
    ON element_range (identifier_range_id);
