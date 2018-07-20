create table `forecast` (
  `id` bigint(20) not null,
  `source` varchar(10) not null,
  `difference` BIGINT(20) not null,
  `is_late` bit(1) not null,
  `version` bigint(20) not null,
  `forecast_time` datetime not null,
  `departure_date` DATE NOT NULL,
  `train_number` BIGINT(20) NOT NULL,
  `attap_id` BIGINT(20) NOT NULL,
  primary key (`id`),
  key `departure_date_trainnumber` (`departure_date`,`train_number`)
);