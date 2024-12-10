ALTER TABLE composition
    ADD message_date_time DATETIME NULL DEFAULT NULL,
    ADD INDEX `composition_message_date_time` (`message_date_time` ASC);

-- Convert ms to seconds
UPDATE composition
SET message_date_time = FROM_UNIXTIME(message_reference/1000)
WHERE message_reference IS NOT NULL;

ALTER TABLE composition
    DROP message_reference;


