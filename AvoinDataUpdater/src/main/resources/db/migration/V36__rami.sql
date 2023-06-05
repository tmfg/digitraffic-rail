CREATE TABLE rami_message
(
    id VARCHAR(64) NOT NULL,
    version INT UNSIGNED NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    created_source DATETIME NOT NULL,
    created_db DATETIME DEFAULT CURRENT_TIMESTAMP,
    modified_db DATETIME ON UPDATE CURRENT_TIMESTAMP,
    start_validity DATETIME NOT NULL,
    end_validity DATETIME NOT NULL,
    train_number BIGINT(20) NULL,
    train_departure_date DATE NULL,
    journey_ref VARCHAR(64) NULL,
    deleted DATETIME NULL,
    PRIMARY KEY (id, version)
);

CREATE TABLE rami_message_station
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    rami_message_id VARCHAR(64) NOT NULL,
    rami_message_version INT UNSIGNED NOT NULL,
    station_short_code VARCHAR(16) NOT NULL,
    CONSTRAINT FK_rami_message_station
        FOREIGN KEY (rami_message_id, rami_message_version) REFERENCES rami_message (id, version)
            ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rami_message_video
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    rami_message_id VARCHAR(64) NOT NULL,
    rami_message_version INT UNSIGNED NOT NULL,
    text_fi TEXT NULL,
    text_sv TEXT NULL,
    text_en TEXT NULL,
    delivery_type VARCHAR(64) NULL,
    start_date_time DATETIME NULL,
    end_date_time DATETIME NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    days_of_week VARCHAR(7) NULL,
    CONSTRAINT FK_rami_message_video
        FOREIGN KEY (rami_message_id, rami_message_version) REFERENCES rami_message (id, version)
            ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE rami_message_audio
(
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        PRIMARY KEY,
    rami_message_id VARCHAR(64) NOT NULL,
    rami_message_version INT UNSIGNED NOT NULL,
    text_fi TEXT NULL,
    text_sv TEXT NULL,
    text_en TEXT NULL,
    delivery_type VARCHAR(64) NULL,
    start_date_time DATETIME NULL,
    end_date_time DATETIME NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    days_of_week VARCHAR(7) NULL,
    delivery_at DATETIME NULL,
    repetitions SMALLINT UNSIGNED NULL,
    repeat_every INT UNSIGNED NULL,
    CONSTRAINT FK_rami_message_audio
        FOREIGN KEY (rami_message_id, rami_message_version) REFERENCES rami_message (id, version)
            ON UPDATE CASCADE ON DELETE CASCADE
);
