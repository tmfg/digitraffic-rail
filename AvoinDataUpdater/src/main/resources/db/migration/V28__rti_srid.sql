DELETE FROM track_work_notification;

ALTER TABLE track_work_notification CHANGE COLUMN location_map location_map GEOMETRY SRID 4326 NOT NULL;
ALTER TABLE track_work_notification CHANGE COLUMN location_schema location_schema GEOMETRY SRID 4326 NOT NULL;

ALTER TABLE ruma_location CHANGE COLUMN location_map location_map GEOMETRY SRID 4326;
ALTER TABLE ruma_location CHANGE COLUMN location_schema location_schema GEOMETRY SRID 4326;

ALTER TABLE identifier_range CHANGE COLUMN location_map location_map GEOMETRY SRID 4326 NOT NULL;
ALTER TABLE identifier_range CHANGE COLUMN location_schema location_schema GEOMETRY SRID 4326 NOT NULL;
