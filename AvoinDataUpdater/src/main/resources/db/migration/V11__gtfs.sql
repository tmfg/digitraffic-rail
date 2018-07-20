CREATE TABLE `gtfs` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `created` datetime NOT NULL,
  `file_name` VARCHAR(45) NOT NULL,
  `data` LONGBLOB NOT NULL,
  PRIMARY KEY (`id`));
