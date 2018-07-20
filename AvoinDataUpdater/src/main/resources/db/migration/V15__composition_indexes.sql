ALTER TABLE `journey_section`
DROP FOREIGN KEY `FK_composition_journey_section`;


ALTER TABLE `composition`
DROP INDEX `composition_departure_date` ,
ADD INDEX `composition_departure_date` (`departure_date` ASC, `train_number` ASC),
DROP INDEX `composition_train_number` ;

ALTER TABLE `journey_section`
ADD CONSTRAINT `FK_composition_journey_section`
  FOREIGN KEY (`departure_date`,`train_number`)
  REFERENCES `composition` (`departure_date`,`train_number`)
  ON DELETE CASCADE
  ON UPDATE CASCADE;