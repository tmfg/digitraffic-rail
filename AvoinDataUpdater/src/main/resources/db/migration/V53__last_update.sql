-- When a data type was last updated successfully. Only for data that is updated rarely (once a day or so),
-- so that the time is not lost when the updater restarts.
CREATE TABLE `last_update` (
    `type` VARCHAR(100) NOT NULL,
    `last_updated` TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (`type`)
);
