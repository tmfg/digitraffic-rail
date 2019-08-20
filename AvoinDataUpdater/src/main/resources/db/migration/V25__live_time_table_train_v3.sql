  CREATE OR REPLACE
      ALGORITHM = UNDEFINED
  VIEW `live_time_table_train` AS
      SELECT
          `ttr`.`train_stopping` AS `train_stopping`,
          `ttr`.`departure_date` AS `departure_date`,
          `ttr`.`train_number` AS `train_number`,
          `t`.`version` AS `version`,
          `ttr`.`type` AS `type`,
          `ttr`.`station_short_code` AS `station_short_code`,
          `ttr`.`actual_time` AS `actual_time`,
          `ttr`.`scheduled_time` AS `scheduled_time`,
          `ttr`.`live_estimate_time` AS `live_estimate_time`,
          COALESCE(`ttr`.`live_estimate_time`, `ttr`.`scheduled_time`) AS `predict_time`,
          `t`.`cancelled` AS `cancelled`,
          `t`.`deleted` AS `deleted`,
          `t`.`train_category_id` AS `train_category_id`
      FROM
          (`time_table_row` `ttr`
          JOIN `train` `t`)
      WHERE
          ((`t`.`train_number` = `ttr`.`train_number`)
              AND (`t`.`departure_date` = `ttr`.`departure_date`)
              AND (`ttr`.`departure_date` BETWEEN (CURDATE() + INTERVAL -(1) DAY) AND (CURDATE() + INTERVAL 1 DAY))
              AND (`ttr`.`scheduled_time` BETWEEN (UTC_TIMESTAMP() + INTERVAL -(1) DAY) AND (UTC_TIMESTAMP() + INTERVAL 1 DAY))
              AND ((`ttr`.`scheduled_time` > (UTC_TIMESTAMP() - INTERVAL 1 HOUR))
              OR (`ttr`.`actual_time` IS NOT NULL)
              OR ((`ttr`.`cancelled` = 1)
              AND (`ttr`.`live_estimate_time` > (UTC_TIMESTAMP() - INTERVAL 15 MINUTE)))
              OR ((`ttr`.`cancelled` = 0)
              AND (`ttr`.`live_estimate_time` > (UTC_TIMESTAMP() - INTERVAL 2 HOUR)))));
