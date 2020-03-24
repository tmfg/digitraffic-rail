ALTER TABLE ruma_location CHANGE COLUMN track_work_part_id track_work_part_id BIGINT UNSIGNED;

CREATE TABLE traffic_restriction_notification
(
    id VARCHAR(64) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    state TINYINT UNSIGNED NOT NULL,
    organization VARCHAR(64) NOT NULL,
    created DATETIME NOT NULL,
    modified DATETIME NULL,
    limitation TINYINT UNSIGNED NOT NULL,
    axle_weight_max DECIMAL (15,2),
    start_date DATETIME NOT NULL,
    end_date DATETIME,
    twn_id VARCHAR(64),
    finished DATETIME,
    location_map GEOMETRY,
    location_schema GEOMETRY,
    PRIMARY KEY (id, version)
);
CREATE INDEX traffic_restriction_notification_modified_id_version_idx ON traffic_restriction_notification
    (
     modified asc,
     id asc,
     version asc
);

ALTER TABLE traffic_restriction_notification
    CHANGE COLUMN location_map location_map GEOMETRY NOT NULL,
    CHANGE COLUMN location_schema location_schema GEOMETRY NOT NULL;

ALTER TABLE ruma_location ADD trn_id VARCHAR(64);
ALTER TABLE ruma_location ADD trn_version BIGINT UNSIGNED;
ALTER TABLE ruma_location ADD CONSTRAINT FK_lri_id
        FOREIGN KEY (trn_id, trn_version) REFERENCES traffic_restriction_notification (id, version)
            ON UPDATE CASCADE ON DELETE CASCADE;
CREATE INDEX FK_trn_id_version_idx
    ON ruma_location (trn_id, trn_version);
