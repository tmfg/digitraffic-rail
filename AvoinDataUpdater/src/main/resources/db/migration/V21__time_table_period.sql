CREATE TABLE `time_table_period` (
  `id` bigint(20) NOT NULL,
  `name` varchar(128) NOT NULL,
  `effective_from` date NOT NULL,
  `effective_to` date NOT NULL,
  `capacity_allocation_confirm_date` date NOT NULL,
  `capacity_request_submission_deadline` date NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `time_table_period_change_date` (
  `id` bigint(20) NOT NULL,
  `time_table_period_id` bigint(20) NOT NULL,
  `effective_from` date DEFAULT NULL,
  `capacity_request_submission_deadline` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `time_table_period_fk_idx` (`time_table_period_id`),
  CONSTRAINT `time_table_period_fk` FOREIGN KEY (`time_table_period_id`) REFERENCES `time_table_period` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
