ALTER TABLE `locomotive`
DROP FOREIGN KEY `FK_locomotive_journey_section`;
ALTER TABLE `locomotive`
DROP INDEX `FK_locomotive_journey_section` ;

ALTER TABLE `wagon`
DROP FOREIGN KEY `FK_wagon_journey_section`;
ALTER TABLE `wagon`
DROP INDEX `journey_section_FK_IDX` ;


ALTER TABLE `journey_section` CHANGE COLUMN `id` `id` BIGINT(20) NOT NULL AUTO_INCREMENT ;


ALTER TABLE `locomotive`
ADD INDEX `FK_locomotive_journey_section_idx` (`journeysection` ASC);
ALTER TABLE `locomotive`
ADD CONSTRAINT `FK_locomotive_journey_section`
  FOREIGN KEY (`journeysection`)
  REFERENCES `journey_section` (`id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;


ALTER TABLE `wagon`
ADD INDEX `FK_wagon_journey_section_idx` (`journeysection` ASC);
ALTER TABLE `wagon`
ADD CONSTRAINT `FK_wagon_journey_section`
  FOREIGN KEY (`journeysection`)
  REFERENCES `journey_section` (`id`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;
