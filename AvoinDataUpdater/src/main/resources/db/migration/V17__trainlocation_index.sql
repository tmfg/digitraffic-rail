ALTER TABLE `train_location`
ADD INDEX `trainId_idx` (`departure_date` ASC, `train_number` ASC, `timestamp` ASC);
