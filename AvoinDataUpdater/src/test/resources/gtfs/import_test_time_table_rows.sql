INSERT INTO avoindata_test.train
(departure_date, train_number, version, cancelled, commuter_lineid, operator_short_code, operator_uic_code, running_currently, train_type_id,
 train_category_id, timetable_acceptance_date, timetable_type, deleted)

VALUES ('2018-12-31', 1, 0, 0, '', '', 0, 0, 0, 0, '2018-12-31', 0, 0),
       ('2018-12-31', 2, 0, 0, '', '', 0, 0, 0, 0, '2018-12-31', 0, 0),
       ('2019-01-01', 1, 0, 0, '', '', 0, 0, 0, 0, '2019-01-01', 0, 0),
       ('2019-01-02', 1, 0, 0, '', '', 0, 0, 0, 0, '2019-01-01', 0, 0),
       ('2019-01-11', 1, 0, 0, '', '', 0, 0, 0, 0, '2019-01-01', 0, 0),
       ('2019-01-12', 1, 0, 0, '', '', 0, 0, 0, 0, '2019-01-01', 0, 0);

INSERT INTO avoindata_test.time_table_row (departure_date, train_number, attap_id, cancelled, scheduled_time, station_uic_code, train_stopping,
                                           `type`)
VALUES ('2018-12-31', 1, 1, 0, '2018-12-31T12:00', 1, 1, 1),
       ('2018-12-31', 2, 2, 0, '2019-01-01T12:00', 1, 1, 1),
       ('2019-01-01', 1, 3, 0, '2019-01-01T12:00', 1, 1, 1),
       ('2019-01-02', 1, 4, 0, '2019-01-02T06:00', 1, 1, 1),
       ('2019-01-11', 1, 5, 0, '2019-01-11T04:00', 1, 1, 1),
       ('2019-01-12', 1, 6, 0, '2019-01-12T04:00', 1, 1, 1);
