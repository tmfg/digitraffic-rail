ALTER TABLE `time_table_row` ADD COLUMN `unknown_delay` BIT(1) NULL DEFAULT NULL;

ALTER TABLE `forecast`
CHANGE COLUMN `difference` `difference` BIGINT(20) NULL ,
CHANGE COLUMN `forecast_time` `forecast_time` DATETIME NULL ;
