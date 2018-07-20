CREATE TABLE `extracted_schedule` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint(20) unsigned NOT NULL,
  `capacity_id` varchar(64) NOT NULL,
  `train_number` bigint(20) NOT NULL,
  `departure_date` date NOT NULL,
  `version` bigint(20) unsigned NOT NULL,
  `timestamp` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `schedule_unique` (`departure_date`,`capacity_id`,`version`)
);
