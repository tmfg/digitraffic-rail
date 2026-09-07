CREATE TABLE `netex_published_journey` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `train_number` bigint(20) NOT NULL,
  `departure_date` date NOT NULL,
  `service_journey_id` varchar(128) NOT NULL,
  `line_id` varchar(128) NOT NULL,
  `operator_ref` varchar(128) DEFAULT NULL,
  `journey_pattern_ref` varchar(128) DEFAULT NULL,
  `dataset_version` bigint(20) unsigned NOT NULL,
  `generated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_npj_version_date` (`dataset_version`,`departure_date`)
);

-- Planned tracks belong to a journey (3NF): reference the parent row via FK instead of duplicating
-- train_number / departure_date / dataset_version. ON DELETE CASCADE prunes tracks when old dataset
-- versions of the parent journeys are deleted.
CREATE TABLE `netex_published_journey_track` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `journey_id` bigint(20) unsigned NOT NULL,
  `station_short_code` varchar(8) NOT NULL,
  `planned_track` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_npjt_journey` (`journey_id`),
  CONSTRAINT `fk_npjt_journey` FOREIGN KEY (`journey_id`) REFERENCES `netex_published_journey` (`id`) ON DELETE CASCADE
);
