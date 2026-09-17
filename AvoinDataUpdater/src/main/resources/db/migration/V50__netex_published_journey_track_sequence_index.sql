-- sequenceIndex is the track's 0-based position in its journey's own visitation order (monotonically
-- increasing regardless of station repeats). It's a separate concept from visit_index (a per-station occurrence
-- counter, reset to 0 for every station's first visit): ordering NeTExPublishedJourney#tracks by visit_index
-- alone moved a repeated station's later visit to the wrong position, misplacing the derived origin/destination
-- refs. See NeTExPublishedJourney#tracks / NeTExPublishedJourneyTrack for details.
ALTER TABLE `netex_published_journey_track`
ADD COLUMN `sequence_index` int NOT NULL DEFAULT 0 AFTER `visit_index`;

-- Backfill existing rows from their insertion (id) order, per journey - the same true journey order the
-- application now writes for every new row - so the newest pre-deployment dataset isn't left with every row
-- at sequence_index 0 (which would leave @OrderBy("sequenceIndex ASC") undefined until the next daily NeTEx
-- regeneration, while SIRI-VM keeps reading it every minute).
UPDATE `netex_published_journey_track` t
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY journey_id ORDER BY id) - 1 AS rn
    FROM `netex_published_journey_track`
) ranked ON ranked.id = t.id
SET t.sequence_index = ranked.rn;
