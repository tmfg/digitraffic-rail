ALTER TABLE composition
    ADD message_reference BIGINT(20) NULL DEFAULT NULL,
    ADD INDEX `composition_message_reference` (`message_reference` ASC);
