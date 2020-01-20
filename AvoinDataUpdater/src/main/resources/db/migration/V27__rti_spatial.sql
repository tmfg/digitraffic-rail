ALTER TABLE track_work_notification
    ADD location_map GEOMETRY,
    ADD location_schema GEOMETRY
;

ALTER TABLE ruma_location
    ADD location_map GEOMETRY,
    ADD location_schema GEOMETRY
;

ALTER TABLE identifier_range
    ADD location_map GEOMETRY,
    ADD location_schema GEOMETRY
;