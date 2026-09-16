-- sequenceIndex is the track's 0-based position in its journey's own visitation order (monotonically
-- increasing regardless of station repeats). It's a separate concept from visit_index (a per-station occurrence
-- counter, reset to 0 for every station's first visit): ordering NeTExPublishedJourney#tracks by visit_index
-- alone moved a repeated station's later visit to the wrong position, misplacing the derived origin/destination
-- refs. See NeTExPublishedJourney#tracks / NeTExPublishedJourneyTrack for details.
ALTER TABLE `netex_published_journey_track`
ADD COLUMN `sequence_index` int NOT NULL DEFAULT 0 AFTER `visit_index`;
