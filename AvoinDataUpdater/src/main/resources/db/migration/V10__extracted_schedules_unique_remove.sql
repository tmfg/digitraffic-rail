ALTER TABLE `extracted_schedule`
DROP INDEX `schedule_unique` ,
ADD INDEX `schedule_unique` (`departure_date` ASC, `capacity_id` ASC, `version` ASC);