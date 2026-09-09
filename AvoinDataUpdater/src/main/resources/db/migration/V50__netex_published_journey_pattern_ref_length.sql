-- A journey_pattern_ref length grows with the number of commercial stops.
ALTER TABLE `netex_published_journey` MODIFY `journey_pattern_ref` TEXT NULL;


ALTER TABLE `netex_published_journey` MODIFY `line_id` varchar(300) NOT NULL;
ALTER TABLE `netex_published_journey` MODIFY `operator_ref` varchar(300) DEFAULT NULL;
