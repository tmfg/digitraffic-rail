DELETE FROM track_work_notification;

ALTER TABLE track_work_notification
    ADD location_map GEOMETRY NOT NULL,
    ADD location_schema GEOMETRY NOT NULL
;

ALTER TABLE ruma_location
    ADD location_map GEOMETRY,
    ADD location_schema GEOMETRY
;

ALTER TABLE identifier_range
    ADD location_map GEOMETRY NOT NULL,
    ADD location_schema GEOMETRY NOT NULL
;