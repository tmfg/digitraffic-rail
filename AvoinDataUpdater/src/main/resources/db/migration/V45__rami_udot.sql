create table if not exists rami_stop_monitoring_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rami_message_id VARCHAR(36) NOT NULL,
    created_db DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    train_number INT UNSIGNED NOT NULL,
    train_departure_date DATE NOT NULL,
    message TEXT NOT NULL
);

create index rsmm_train_number_i on rami_stop_monitoring_message(train_number, train_departure_date);
create index rsmm_created_i on rami_stop_monitoring_message(created_db);

create table if not exists rami_udot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    train_number INT UNSIGNED NOT NULL,
    train_departure_date DATE NOT NULL,
	attap_id BIGINT UNSIGNED NOT NULL,
    created_db DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
	modified_db DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
   	model_updated_time DATETIME,
	unknown_track BOOLEAN NOT NULL,
	unknown_delay BOOLEAN NOT NULL
);

create unique index rami_udot_at_u on rami_udot(train_departure_date, train_number, attap_id);
create index rami_udot_created_i on rami_udot(created_db);

create trigger rami_udot_before_update BEFORE UPDATE on rami_udot for each row BEGIN
    IF (NOT (OLD.unknown_track <=> NEW.unknown_track) OR NOT (OLD.unknown_delay <=> NEW.unknown_delay)) THEN
	   set new.model_updated_time = null;
    END IF;
END;

create table if not exists rami_udot_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rami_message_id VARCHAR(36) NOT NULL,
    train_number INT UNSIGNED NOT NULL,
    train_departure_date DATE NOT NULL,
    attap_id BIGINT UNSIGNED NOT NULL,
    created_db DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    unknown_track BOOLEAN NOT NULL,
    unknown_delay BOOLEAN NOT NULL
);

create index rami_udot_history_created_i on rami_udot_history(created_db);

ALTER TABLE `time_table_row` ADD COLUMN `unknown_track` BIT(1) NULL DEFAULT NULL;