ALTER TABLE `time_table_row`
ADD INDEX `live_IDX` (`station_short_code` ASC, `train_stopping` ASC, `type` ASC, `departure_date` ASC),
DROP INDEX `live_tttr_IDX` ,
DROP INDEX `timetablerow_station_short_code_IDX` ;
