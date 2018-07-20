CREATE TABLE `train_location` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `departure_date` date NOT NULL,
  `train_number` bigint(20) NOT NULL,
  `timestamp` datetime NOT NULL,
  `location` point NOT NULL,
  `speed` int(11) NOT NULL,
  `connection_quality` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `timestamp_idx` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;