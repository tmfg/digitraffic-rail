ALTER TABLE journey_section
    ADD COLUMN `attap_id`      BIGINT(20) NULL DEFAULT NULL,
    ADD COLUMN `saap_attap_id` BIGINT(20) NULL DEFAULT NULL;
