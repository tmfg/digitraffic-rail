CREATE TABLE IF NOT EXISTS `gtfs_trip` (
    `train_number`  BIGINT(20) NOT NULL,
    `start_date`    DATE NOT NULL,
    `end_date`      DATE NULL,
    `trip_id`       VARCHAR(255) NOT NULL,
    `route_id`      VARCHAR(255) NOT NULL,
    PRIMARY KEY (`train_number`, `start_date`))
ENGINE = InnoDB
;