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

    // Regression test mirroring GTFSTrainRepository.getTrainLocations' `term` lateral join fallback: once a
    // train has arrived at its terminus (the terminus' only row, an ARRIVAL, now has an actual time), there is
    // no not-yet-completed row left for currentVisitIndex to match, so without the terminus fallback it would
    // wrongly return empty - losing the planned-track lookup VmJourneyConverter needs when the live track is
    // unknown.
    @Test
    public void currentVisitIndex_returnsArrivedTerminusAsCurrent() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE");

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    @Test
    public void currentVisitIndex_emptyWhenStationNeverOccurs() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE");

        assertTrue(visitIndex.isEmpty());
    }

    // Regression test: a station's DEPARTURE and the next station's ARRIVAL can share the exact same
    // scheduled_time (zero scheduled transit time between adjacent stops - a real occurrence, see
    // TrainFactory's TPE DEPARTURE / JY ARRIVAL fixture rows). Sorting ARRIVAL-first on that tie would let
    // JY's ARRIVAL slot in between TPE's own ARRIVAL/DEPARTURE pair, pairing TPE's ARRIVAL with no departure
    // (mistaken for a terminus) and JY's ARRIVAL with TPE's DEPARTURE (wrong station identity) - corrupting
    // every visitIndex from that point on. DEPARTURE must win the tie so pairing stays station-correct.
    @Test
    public void of_pairsRowsCorrectlyWhenDepartureAndNextArrivalTie() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(2), false, null));
        // JY's ARRIVAL ties exactly with TPE's DEPARTURE above.
        rows.add(row("JY", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(2), false, null));
        rows.add(row("JY", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(3), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);

        assertEquals(3, stops.size());
        assertEquals("HKI", stops.get(0).departure().stationShortCode);
        assertEquals("TPE", stops.get(1).arrival().stationShortCode);
        assertEquals("JY", stops.get(2).arrival().stationShortCode);
        // TPE must be a full arrival+departure pair, not mistaken for a terminus (departure == null).
        assertTrue(stops.get(1).departure() != null);
        assertEquals("TPE", stops.get(1).departure().stationShortCode);
        // The train is still dwelling at TPE (its DEPARTURE has no actual time yet) - not yet at JY.
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE");
        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }
}
