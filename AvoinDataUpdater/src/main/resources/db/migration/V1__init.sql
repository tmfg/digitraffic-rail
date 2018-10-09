-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='TRADITIONAL,ALLOW_INVALID_DATES';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Table `category_code`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `category_code` (
  `category_code` VARCHAR(255) NOT NULL,
  `category_name` VARCHAR(255) NOT NULL,
  `id` BIGINT(20) NOT NULL,
  `valid_from` DATE NULL DEFAULT NULL,
  `valid_to` DATE NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `category_code_idx_u` (`id` ASC))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `train`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `train` (
  `departure_date` DATE NOT NULL,
  `train_number` BIGINT(20) NOT NULL,
  `version` BIGINT(20) NOT NULL,
  `cancelled` BIT(1) NOT NULL,
  `commuter_lineid` VARCHAR(255) NULL DEFAULT NULL,
  `operator_short_code` VARCHAR(255) NOT NULL,
  `operator_uic_code` INT(11) NOT NULL,
  `running_currently` BIT(1) NOT NULL,
  `train_type_id` BIGINT(20) NOT NULL,
  `train_category_id` BIGINT(20) NOT NULL,
  `timetable_acceptance_date` DATETIME NOT NULL,
  `timetable_type` INT(11) NOT NULL,
  PRIMARY KEY (`departure_date`, `train_number`),
  INDEX `train_version_IDX` (`version` ASC))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `train_ready`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `train_ready` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `accepted` BIT(32) NOT NULL,
  `source` VARCHAR(32) NOT NULL,
  `timestamp` DATETIME NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `time_table_row`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `time_table_row` (
  `departure_date` DATE NOT NULL,
  `train_number` BIGINT(20) NOT NULL,
  `attap_id` BIGINT(20) NOT NULL,
  `actual_time` DATETIME NULL DEFAULT NULL,
  `cancelled` BIT(1) NOT NULL,
  `commercial_track` VARCHAR(4) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `difference_in_minutes` BIGINT(20) NULL DEFAULT NULL,
  `live_estimate_time` DATETIME NULL DEFAULT NULL,
  `scheduled_time` DATETIME NOT NULL,
  `country_code` VARCHAR(2) CHARACTER SET 'utf8' NULL DEFAULT NULL,
  `station_short_code` VARCHAR(8) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `station_uic_code` INT(11) NOT NULL,
  `train_stopping` BIT(1) NOT NULL,
  `type` INT(11) NOT NULL,
  `commercial_stop` BIT(1) NULL DEFAULT NULL,
  `train_ready_id` BIGINT(20) NULL DEFAULT NULL,
  PRIMARY KEY (`departure_date`, `train_number`, `attap_id`),
  INDEX `timetablerow_station_short_code_IDX` (`station_short_code` ASC),
  INDEX `live_tttr_IDX` (`departure_date` ASC, `scheduled_time` ASC, `station_short_code` ASC, `type` ASC, `actual_time` ASC, `live_estimate_time` ASC, `train_stopping` ASC, `train_number` ASC),
  INDEX `train_FK_IDX` USING BTREE (`departure_date` ASC, `train_number` ASC),
  INDEX `train_ready_FK_idx` (`train_ready_id` ASC),
  CONSTRAINT `train_FK`
    FOREIGN KEY (`departure_date` , `train_number`)
    REFERENCES `train` (`departure_date` , `train_number`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `train_ready_FK`
    FOREIGN KEY (`train_ready_id`)
    REFERENCES `train_ready` (`id`)
    ON DELETE CASCADE)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8
COLLATE = utf8_swedish_ci;


-- -----------------------------------------------------
-- Table `cause`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `cause` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `category_code_id` BIGINT(20) NULL DEFAULT NULL,
  `departure_date` DATE NOT NULL,
  `train_number` BIGINT(20) NOT NULL,
  `attap_id` BIGINT(20) NOT NULL,
  `detailed_category_code_id` BIGINT(20) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `time_table_row_FK_idx` (`departure_date` ASC, `train_number` ASC, `attap_id` ASC),
  CONSTRAINT `time_table_row_FK`
    FOREIGN KEY (`departure_date` , `train_number` , `attap_id`)
    REFERENCES `time_table_row` (`departure_date` , `train_number` , `attap_id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `cause_identity_generator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `cause_identity_generator` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `sequence_name` VARCHAR(100) NOT NULL,
  `next_hi_value` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `composition`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `composition` (
  `departure_date` DATE NOT NULL,
  `train_number` BIGINT(20) NOT NULL,
  `operator_short_code` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `train_category_id` BIGINT(20) NULL DEFAULT NULL,
  `train_type_id` BIGINT(20) NULL DEFAULT NULL,
  `version` BIGINT(20) NULL DEFAULT NULL,
  `operator_uic_code` INT(11) NULL DEFAULT NULL,
  PRIMARY KEY (`departure_date`, `train_number`),
  INDEX `composition_train_number` (`train_number` ASC),
  INDEX `composition_departure_date` (`departure_date` ASC),
  INDEX `composition_version` (`version` ASC))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `composition_time_table_row`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `composition_time_table_row` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `scheduled_time` DATETIME NULL DEFAULT NULL,
  `country_code` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `station_short_code` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `station_uic_code` INT(11) NULL DEFAULT NULL,
  `type` INT(11) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `composition_ttr_identity_generator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `composition_ttr_identity_generator` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `sequence_name` VARCHAR(100) NOT NULL,
  `next_hi_value` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `detailed_category_code`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `detailed_category_code` (
  `detailed_category_code` VARCHAR(255) COLLATE utf8_swedish_ci NOT NULL,
  `detailed_category_name` VARCHAR(255) COLLATE utf8_swedish_ci NOT NULL,
  `id` BIGINT(20) NOT NULL,
  `category_code_id` BIGINT(20) NULL DEFAULT NULL,
  `valid_from` DATE NULL DEFAULT NULL,
  `valid_to` DATE NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `generic_identity_generator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `generic_identity_generator` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `sequence_name` VARCHAR(100) NOT NULL,
  `next_hi_value` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `journey_section`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `journey_section` (
  `id` BIGINT(20) NOT NULL,
  `departure_date` DATE NOT NULL,
  `train_number` BIGINT(20) NOT NULL,
  `begin_time_table_row_id` BIGINT(20) NULL DEFAULT NULL,
  `end_time_table_row_id` BIGINT(20) NULL DEFAULT NULL,
  `maximum_speed` INT(11) NOT NULL,
  `total_length` INT(11) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `FK_begin_composition_ttr` (`begin_time_table_row_id` ASC),
  INDEX `FK_end_composition_ttr` (`end_time_table_row_id` ASC),
  INDEX `FK_composition_journey_section_idx` (`train_number` ASC, `departure_date` ASC),
  CONSTRAINT `FK_begin_composition_ttr`
    FOREIGN KEY (`begin_time_table_row_id`)
    REFERENCES `composition_time_table_row` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `FK_composition_journey_section`
    FOREIGN KEY (`train_number` , `departure_date`)
    REFERENCES `composition` (`train_number` , `departure_date`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `FK_end_composition_ttr`
    FOREIGN KEY (`end_time_table_row_id`)
    REFERENCES `composition_time_table_row` (`id`))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `journey_section_identity_generator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `journey_section_identity_generator` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `sequence_name` VARCHAR(100) NOT NULL,
  `next_hi_value` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `locomotive`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `locomotive` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `location` INT(11) NULL DEFAULT NULL,
  `locomotive_type` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `power_type_abbreviation` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `journeysection` BIGINT(20) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `FK_locomotive_journey_section` (`journeysection` ASC),
  CONSTRAINT `FK_locomotive_journey_section`
    FOREIGN KEY (`journeysection`)
    REFERENCES `journey_section` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `locomotive_identity_generator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `locomotive_identity_generator` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `sequence_name` VARCHAR(100) NOT NULL,
  `next_hi_value` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `operator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `operator` (
  `id` BIGINT(20) NOT NULL,
  `operator_name` VARCHAR(64) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `operator_short_code` VARCHAR(255) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci  NULL DEFAULT NULL,
  `operator_uic_code` INT(11) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8
COLLATE = utf8_swedish_ci;


-- -----------------------------------------------------
-- Table `operator_train_number`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `operator_train_number` (
  `id` BIGINT(20) NOT NULL,
  `bottom_limit` INT(11) NOT NULL,
  `top_limit` INT(11) NOT NULL,
  `train_category` VARCHAR(64) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `operator_id` BIGINT(20) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `FK_1bq2tnoht0977hc2tf0lqcar7` (`operator_id` ASC),
  CONSTRAINT `FK_1bq2tnoht0977hc2tf0lqcar7`
    FOREIGN KEY (`operator_id`)
    REFERENCES `operator` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8
COLLATE = utf8_swedish_ci;


-- -----------------------------------------------------
-- Table `power_type`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `power_type` (
  `id` BIGINT(20) NOT NULL,
  `abbreviation` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `name` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
;

-- -----------------------------------------------------
-- Table `station`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `station` (
  `id` BIGINT(20) NOT NULL,
  `passenger_traffic` BIT(1) NOT NULL,
  `country_code` VARCHAR(2) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `latitude` DECIMAL(17,14) NULL DEFAULT NULL,
  `longitude` DECIMAL(17,14) NULL DEFAULT NULL,
  `name` VARCHAR(64) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `short_code` VARCHAR(8) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  `uic_code` INT(11) NOT NULL,
  `type` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8
COLLATE = utf8_swedish_ci;


-- -----------------------------------------------------
-- Table `system_state_property`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_state_property` (
  `id` VARCHAR(255) NOT NULL,
  `value` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `track_section`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `track_section` (
  `id` BIGINT(20) NOT NULL,
  `station` VARCHAR(8) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NOT NULL,
  `track_section_code` VARCHAR(30) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8
COLLATE = utf8_swedish_ci;


-- -----------------------------------------------------
-- Table `track_range`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `track_range` (
  `id` BIGINT(20) NOT NULL,
  `end_kilometres` INT(11) NOT NULL,
  `end_metres` INT(11) NOT NULL,
  `end_track` VARCHAR(16) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NOT NULL,
  `start_kilometres` INT(11) NOT NULL,
  `start_metres` INT(11) NOT NULL,
  `start_track` VARCHAR(16) CHARACTER SET 'utf8' COLLATE utf8_swedish_ci NOT NULL,
  `track_section_id` BIGINT(20) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `FK_bfx4eqgh1ojw5kunt33nr9pt3` (`track_section_id` ASC),
  CONSTRAINT `FK_bfx4eqgh1ojw5kunt33nr9pt3`
    FOREIGN KEY (`track_section_id`)
    REFERENCES `track_section` (`id`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8
COLLATE = utf8_swedish_ci;


-- -----------------------------------------------------
-- Table `train_category`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `train_category` (
  `id` BIGINT(20) NOT NULL,
  `name` VARCHAR(255) COLLATE utf8_swedish_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `train_running_message`
-- -----------------------------------------------------
CREATE TABLE `train_running_message` (
  `id` bigint(20) NOT NULL,
  `next_track_section` varchar(30) COLLATE utf8_swedish_ci DEFAULT NULL,
  `previous_track_section` varchar(30) COLLATE utf8_swedish_ci DEFAULT NULL,
  `timestamp` datetime(6) NOT NULL,
  `track_section` varchar(30) COLLATE utf8_swedish_ci NOT NULL,
  `departure_date` date DEFAULT NULL,
  `train_number` varchar(8) COLLATE utf8_swedish_ci NOT NULL,
  `type` int(11) NOT NULL,
  `station` varchar(8) COLLATE utf8_swedish_ci NOT NULL,
  `next_station` varchar(8) COLLATE utf8_swedish_ci DEFAULT NULL,
  `previous_station` varchar(8) COLLATE utf8_swedish_ci DEFAULT NULL,
  `version` bigint(20) NOT NULL,
  `virtual_departure_date` date ,
  PRIMARY KEY (`id`),
  KEY `tr20_version` (`version`),
  KEY `tr20_virtualDepartureDate_trainNumber` (`virtual_departure_date`,`train_number`),
  KEY `tr20_station_trackSection_virtualDepartureDate` (`station`,`track_section`,`virtual_departure_date`),
  KEY `tr20_station_virtualDepartureDate` (`station`,`virtual_departure_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_swedish_ci;


DELIMITER //

CREATE TRIGGER train_running_message_before_insert
BEFORE INSERT ON train_running_message
FOR EACH ROW BEGIN
  IF(NEW.departure_date IS NULL) THEN
    SET NEW.virtual_departure_date = cast(convert_tz(NEW.timestamp,'UTC','Europe/Helsinki') as date);
  ELSE
    SET NEW.virtual_departure_date = new.departure_date;
  END IF;
END; //

DELIMITER ;


-- -----------------------------------------------------
-- Table `train_running_message_rule`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `train_running_message_rule` (
  `id` BIGINT(20) NOT NULL,
  `time_table_row_station_short_code` VARCHAR(8) COLLATE utf8_swedish_ci NOT NULL,
  `time_table_row_type` INT(11) NOT NULL,
  `train_running_message_station_short_code` VARCHAR(8) COLLATE utf8_swedish_ci NOT NULL,
  `train_running_message_type` INT(11) NOT NULL,
  `train_running_message_next_station_short_code` VARCHAR(8) COLLATE utf8_swedish_ci NOT NULL,
  `train_running_message_track_section` VARCHAR(16) COLLATE utf8_swedish_ci NOT NULL,
  `offset` INT(3) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `train_type`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `train_type` (
  `id` BIGINT(20) NOT NULL,
  `name` VARCHAR(255) NULL DEFAULT NULL,
  `train_category_id` BIGINT(20) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `train_category_fk_idx` (`train_category_id` ASC),
  CONSTRAINT `train_category_fk_idx`
    FOREIGN KEY (`train_category_id`)
    REFERENCES `train_category` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB
;


-- -----------------------------------------------------
-- Table `wagon`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `wagon` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `catering` BIT(1) NULL DEFAULT NULL,
  `disabled` BIT(1) NULL DEFAULT NULL,
  `length` INT(11) NULL DEFAULT NULL,
  `location` INT(11) NULL DEFAULT NULL,
  `luggage` BIT(1) NULL DEFAULT NULL,
  `pet` BIT(1) NULL DEFAULT NULL,
  `playground` BIT(1) NULL DEFAULT NULL,
  `sales_number` INT(11) NULL DEFAULT NULL,
  `smoking` BIT(1) NULL DEFAULT NULL,
  `video` BIT(1) NULL DEFAULT NULL,
  `journeysection` BIGINT(20) NOT NULL,
  `wagon_type` VARCHAR(255) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `journey_section_FK_IDX` (`journeysection` ASC),
  CONSTRAINT `FK_wagon_journey_section`
    FOREIGN KEY (`journeysection`)
    REFERENCES `journey_section` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE)
ENGINE = InnoDB

;


-- -----------------------------------------------------
-- Table `wagon_identity_generator`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `wagon_identity_generator` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `sequence_name` VARCHAR(100) NOT NULL,
  `next_hi_value` INT(11) NOT NULL,
  PRIMARY KEY (`id`))
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Placeholder table for view `live_time_table_train`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `live_time_table_train` (`train_stopping` INT, `departure_date` INT, `train_number` INT, `version` INT, `type` INT, `station_short_code` INT, `actual_time` INT, `scheduled_time` INT, `live_estimate_time` INT, `predict_time` INT);

-- -----------------------------------------------------
-- View `live_time_table_train`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `live_time_table_train`;
CREATE  OR REPLACE ALGORITHM=UNDEFINED VIEW `live_time_table_train` AS select `ttr`.`train_stopping` AS `train_stopping`,`ttr`.`departure_date` AS `departure_date`,`ttr`.`train_number` AS `train_number`,`t`.`version` AS `version`,`ttr`.`type` AS `type`,`ttr`.`station_short_code` AS `station_short_code`,`ttr`.`actual_time` AS `actual_time`,`ttr`.`scheduled_time` AS `scheduled_time`,`ttr`.`live_estimate_time` AS `live_estimate_time`,coalesce(`ttr`.`live_estimate_time`,`ttr`.`scheduled_time`) AS `predict_time` from (`time_table_row` `ttr` join `train` `t`) where ((`t`.`train_number` = `ttr`.`train_number`) and (`t`.`departure_date` = `ttr`.`departure_date`) and (`ttr`.`departure_date` between (curdate() + interval -(1) day) and (curdate() + interval 1 day)) and (`ttr`.`scheduled_time` between (utc_timestamp() + interval -(1) day) and (utc_timestamp() + interval 1 day)) and ((`ttr`.`scheduled_time` > (utc_timestamp() - interval 1 hour)) or (`ttr`.`actual_time` is not null) or ((`ttr`.`cancelled` = 1) and (`ttr`.`live_estimate_time` > (utc_timestamp() - interval 15 minute))) or ((`ttr`.`cancelled` = 0) and (`ttr`.`live_estimate_time` > (utc_timestamp() - interval 2 hour)))));

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
