package fi.livi.rata.avoindata.updater.service.siri.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

public class CommercialStopVisitsTest {

    private static final ZonedDateTime T0 = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private static GTFSTimeTableRow row(final String station, final TimeTableRow.TimeTableRowType type,
            final ZonedDateTime scheduledTime, final boolean cancelled, final ZonedDateTime actualTime) {
        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.stationShortCode = station;
        row.type = type;
        row.scheduledTime = scheduledTime;
        row.commercialStop = true;
        row.cancelled = cancelled;
        row.actualTime = actualTime;
        return row;
    }

    @Test
    public void currentVisitIndex_findsFirstNotYetCompletedVisit() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, null));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1).plusMinutes(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE");

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    // Regression test for a bug where a cancelled visit with no actual completion time (the normal state for a
    // cancelled stop, since it's never actually run) was wrongly treated as "current". The live-location query
    // (GTFSTrainRepository.getTrainLocations) excludes cancelled rows outright, so once it resolves a station, it
    // can only ever be a non-cancelled visit - currentVisitIndex must mirror that instead of matching the
    // earlier, cancelled occurrence.
    @Test
    public void currentVisitIndex_skipsCancelledVisitButKeepsItInTheCount() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        // First visit to TPE is cancelled - never actually run, so its arrival/departure never get an actual
        // time either.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0, true, null));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusMinutes(2), true, null));
        // Second visit to TPE (e.g. the train loops back through it) is the real, live one.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(2), false, null));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(2).plusMinutes(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE");

        assertTrue(visitIndex.isPresent());
        // Must resolve to the second (real, live) visit, not the first (cancelled) one - but the cancelled visit
        // still occupies visitIndex 0, so the real visit keeps its true index of 1 (matching the persisted
        // NeTExPublishedJourneyTrack's visitIndex for the same occurrence).
        assertEquals(1, visitIndex.getAsInt());
    }

    @Test
    public void currentVisitIndex_emptyWhenStationNeverOccurs() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE");

        assertTrue(visitIndex.isEmpty());
    }
}
