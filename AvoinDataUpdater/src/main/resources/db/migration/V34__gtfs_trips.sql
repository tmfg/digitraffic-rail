CREATE TABLE IF NOT EXISTS `gtfs_trip` (
    `train_number`  BIGINT(20) NOT NULL,
    `start_date`    DATE NOT NULL,
    `end_date`      DATE NOT NULL,
    `trip_id`       VARCHAR(255) NOT NULL,
    `route_id`      VARCHAR(255) NOT NULL,
    `version`       BIGINT(20) NOT NULL,
    PRIMARY KEY (`train_number`, `start_date`, `end_date`))
ENGINE = InnoDB
;