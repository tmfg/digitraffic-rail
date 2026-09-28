package fi.livi.rata.avoindata.common.domain.gtfs;

import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

/**
 * A track a train was actually seen on. Deliberately not an entity: history is read
 * over a long window inside a transaction, and managed rows would be retained for
 * its whole duration.
 */
public record TrackObservation(Long trainNumber, Long attapId, String stationShortCode,
        TimeTableRow.TimeTableRowType type, String commercialTrack, ZonedDateTime scheduledTime) {
}
