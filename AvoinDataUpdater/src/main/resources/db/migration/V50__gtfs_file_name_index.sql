-- Realtime feeds insert thousands of rows a day, so "newest row for this file name" degraded into a long
-- backward scan of the primary key whenever a daily package was not regenerated.
CREATE INDEX `gtfs_file_name_id_IDX` ON `gtfs` (`file_name`, `id` DESC);
