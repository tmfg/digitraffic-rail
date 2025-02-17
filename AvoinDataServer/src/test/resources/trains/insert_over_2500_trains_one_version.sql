START TRANSACTION $$

DROP PROCEDURE IF EXISTS insert_test_trains $$

CREATE PROCEDURE insert_test_trains()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 2501 DO
            INSERT INTO train (
                train_number,
                departure_date,
                version,
                operator_uic_code,
                operator_short_code,
                train_category_id,
                train_type_id,
                commuter_lineid,
                running_currently,
                cancelled,
                timetable_type,
                timetable_acceptance_date
            ) VALUES (
                         i,                   -- train_number
                         '2023-01-01',        -- departure_date
                         1,                   -- version
                         1,                   -- operator_uic_code
                         'test',              -- operator_short_code
                         1,                   -- train_category_id
                         1,                   -- train_type_id
                         'Z',                 -- commuter_lineid
                         true,                -- running_currently
                         false,               -- cancelled
                         0,                   -- timetable_type
                         '2023-01-01'         -- timetable_acceptance_date
                     );
            INSERT INTO time_table_row (
                departure_date,
                train_number,
                attap_id,
                actual_time,
                cancelled,
                commercial_track,
                difference_in_minutes,
                live_estimate_time,
                scheduled_time,
                country_code,
                station_short_code,
                station_uic_code,
                train_stopping,
                type,
                commercial_stop
            ) VALUES (
                         '2023-01-01',        -- departure_date
                         i,                   -- train_number
                         1,                   -- attap_id
                         '2023-01-01 10:00:00', -- actual_time
                         0,                   -- cancelled
                         '1',                 -- commercial_track
                         0,                   -- difference_in_minutes
                         '2023-01-01 10:05:00', -- live_estimate_time
                         '2023-01-01 10:00:00', -- scheduled_time
                         'FI',                -- country_code
                         'HKI',               -- station_short_code
                         1,                   -- station_uic_code
                         1,                   -- train_stopping
                         1,                   -- type
                         1                    -- commercial_stop
                     );
            SET i = i + 1;
        END WHILE;
END $$

CALL insert_test_trains() $$

DROP PROCEDURE insert_test_trains $$

COMMIT $$