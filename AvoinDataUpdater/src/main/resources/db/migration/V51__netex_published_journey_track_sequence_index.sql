-- sequence_index is the track's 0-based position in the journey's visit order, always increasing even when a
-- station repeats. Unlike visit_index (a per-station counter, reset per station), ordering by visit_index alone
-- put a repeated station's later visit in the wrong place, breaking the derived origin/destination. See
-- NeTExPublishedJourney#tracks / NeTExPublishedJourneyTrack.
ALTER TABLE `netex_published_journey_track`
ADD COLUMN `sequence_index` int NOT NULL DEFAULT 0 AFTER `visit_index`;

-- Backfill from insertion (id) order per journey, matching what the app now writes for new rows. Otherwise
-- every existing row stays at 0 until the next daily NeTEx run, leaving the sort order undefined while
-- SIRI-VM keeps reading it every minute.
UPDATE `netex_published_journey_track` t
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY journey_id ORDER BY id) - 1 AS rn
    FROM `netex_published_journey_track`
) ranked ON ranked.id = t.id
SET t.sequence_index = ranked.rn;
