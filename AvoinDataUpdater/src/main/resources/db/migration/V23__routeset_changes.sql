ALTER TABLE `routeset`
ADD COLUMN `message_id` BIGINT(20) NOT NULL AFTER `virtual_departure_date`;

ALTER TABLE `routeset`
CHANGE COLUMN `route_type` `route_type` VARCHAR(1) NOT NULL ,
CHANGE COLUMN `train_number` `train_number` VARCHAR(8) NOT NULL ,
CHANGE COLUMN `client_system` `client_system` VARCHAR(8) NOT NULL ;

ALTER TABLE `routesection`
CHANGE COLUMN `section_id` `section_id` VARCHAR(30) NOT NULL ,
CHANGE COLUMN `station_code` `station_code` VARCHAR(8) NOT NULL ,
CHANGE COLUMN `commercial_track_id` `commercial_track_id` VARCHAR(8) NULL DEFAULT NULL ,
CHANGE COLUMN `section_order` `section_order` BIGINT(20) NOT NULL ;

ALTER TABLE `time_table_row`
CHANGE COLUMN `commercial_track` `commercial_track` VARCHAR(8) NULL DEFAULT NULL ;
