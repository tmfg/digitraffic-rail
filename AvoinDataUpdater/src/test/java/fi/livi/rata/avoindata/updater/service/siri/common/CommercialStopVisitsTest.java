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
        return row(station, type, scheduledTime, cancelled, actualTime, actualTime == null ? scheduledTime : null);
    }

    private static GTFSTimeTableRow row(final String station, final TimeTableRow.TimeTableRowType type,
            final ZonedDateTime scheduledTime, final boolean cancelled, final ZonedDateTime actualTime,
            final ZonedDateTime liveEstimateTime) {
        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.stationShortCode = station;
        row.type = type;
        row.scheduledTime = scheduledTime;
        row.commercialStop = true;
        row.cancelled = cancelled;
        row.actualTime = actualTime;
        row.liveEstimateTime = liveEstimateTime;
        return row;
    }

    @Test
    public void currentVisitIndex_findsFirstNotYetCompletedVisit() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, null));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1).plusMinutes(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusMinutes(30));

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    // Regression test: while a train is still APPROACHING a stop (not yet arrived), only its ARRIVAL row
    // typically carries a live estimate yet - the DEPARTURE hasn't been reached, let alone estimated, so it has
    // no live estimate at all. The live-location query resolves this stop via that ARRIVAL row (its own
    // eligibility test is per-row, not "does this stop's completing/departure row qualify"), so
    // currentVisitIndex must check either leg too, not just the departure - otherwise it would wrongly miss
    // this stop entirely (empty) even though the live query has already resolved it.
    @Test
    public void currentVisitIndex_findsApproachingVisitViaArrivalRowWhenDepartureHasNoEstimateYet() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        // TPE ARRIVAL: not yet happened, but its live estimate is set and still in the future.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, null, T0.plusHours(1)));
        // TPE DEPARTURE: not yet happened, and has no live estimate at all yet (train hasn't arrived).
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1).plusMinutes(2), false, null,
                null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusMinutes(30));

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    // Regression test for the review-reported bug: cancellation/commercial-stop filters must be applied
    // per-row, not to the whole paired stop. Mirrors
    // GTFSTrainRepositoryTest#getTrainLocationsCancelledArrivalTreatedAsNoPairedArrival: PSL's ARRIVAL is
    // cancelled (so it simply drops out of the SQL's candidate window - a cancelled row is invisible to the
    // query, not a reason to disqualify the whole stop), while PSL's DEPARTURE is perfectly eligible on its
    // own. Before the fix, Stop.isCancelled() (true because ARRIVAL was cancelled) gated the whole stop's
    // eligibility, wrongly hiding this visit even though its DEPARTURE alone is exactly what the live query
    // would resolve.
    @Test
    public void currentVisitIndex_findsVisitViaEligibleDepartureWhenOnlyArrivalIsCancelled() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        // PSL ARRIVAL: cancelled, never actually recorded as happened - drops out of eligibility on its own.
        rows.add(row("PSL", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), true, null));
        // PSL DEPARTURE: not cancelled, not yet happened, with a future live estimate - eligible on its own.
        rows.add(row("PSL", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1).plusMinutes(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "PSL", T0.plusMinutes(30));

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
        final OptionalInt visitIndex =
                CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusHours(1).plusMinutes(30));

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
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusHours(2));

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    // Regression test for the review-reported bug: the terminus fallback's cancellation check must be per-row
    // (mirroring the SQL term lateral's own `tr.cancelled is false` row filter), not "is either leg of the
    // paired stop cancelled". Here TPE's ARRIVAL is not cancelled and has already happened (actual time set),
    // but its DEPARTURE was separately cancelled (e.g. the onward leg was dropped, ending the journey there in
    // practice). Before the fix, Stop.isCancelled() (true because the DEPARTURE was cancelled) gated the whole
    // stop, wrongly hiding this arrived terminus even though the SQL's term lateral - which filters cancelled
    // rows individually - still resolves TPE's ARRIVAL as the last non-cancelled commercial row and reports it.
    @Test
    public void currentVisitIndex_returnsArrivedTerminusAsCurrentWhenOnlyItsDepartureIsCancelled() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        // TPE ARRIVAL: not cancelled, already happened - a perfectly valid terminus candidate on its own.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));
        // TPE DEPARTURE: cancelled - drops out of the SQL's candidate window on its own, but must not
        // disqualify the still-valid ARRIVAL leg of the same stop.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1).plusMinutes(2), true, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusHours(2));

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    // Regression test for the review-reported bug: the terminus fallback must report the *matched* stop's own
    // occurrence index, not the station's final aggregate visit count. Here the real, arrived terminus is
    // TPE's first (visit 0) occurrence; a later, wholly cancelled duplicate TPE row follows it (e.g. a stray
    // erroneous timetable entry) and is skipped by the reverse scan, but it still bumps the station's total
    // visit count to 2. Before the fix, the fallback returned visitCounts.get("TPE") - 1 == 1 (the *last*
    // occurrence's index) regardless of which occurrence it actually matched, so VM would have resolved the
    // planned track for the wrong (later, nonexistent) visit instead of visit 0's.
    @Test
    public void currentVisitIndex_returnsMatchedOccurrencesOwnIndexNotTheStationsFinalCount() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        // TPE visit 0: the real, arrived terminus - not cancelled, already happened, no departure follows.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));
        // TPE visit 1: a later, wholly cancelled duplicate row - invisible to the SQL's term lateral (cancelled
        // rows are filtered out per-row), but still counted as an occurrence for visit-index purposes.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(2), true, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusHours(3));

        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt(), "must resolve to TPE's visit 0 (the actual match), not visit 1");
    }

    @Test
    public void currentVisitIndex_emptyWhenStationNeverOccurs() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0);

        assertTrue(visitIndex.isEmpty());
    }

    // Regression test: the live-location query only treats a not-yet-actual row as eligible while its
    // live_estimate_time is still in the future (see GTFSTrainRepository.getTrainLocations: "actual_time is
    // null and live_estimate_time > CURRENT_TIMESTAMP()"). A repeated station's first visit can be stuck with
    // no actual time yet a *stale* (past) estimate - e.g. delay data hasn't refreshed - in which case the SQL
    // query itself has already moved past it to the next eligible row. currentVisitIndex must mirror that and
    // not return the stale first visit as current.
    @Test
    public void currentVisitIndex_skipsVisitWithStaleEstimateButNoActualTime() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        // First TPE visit: arrived, but its departure has no actual time and a stale (already-past) estimate.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusMinutes(50), false, T0.plusMinutes(50)));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusMinutes(55), false, null,
                T0.plusMinutes(55)));
        // Second TPE visit: further out, with a fresh (future) estimate.
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(2), false, null,
                T0.plusHours(2).plusMinutes(5)));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(2).plusMinutes(5), false, null,
                T0.plusHours(2).plusMinutes(5)));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        // "now" is after the first visit's stale estimate but before the second visit's fresh one.
        final OptionalInt visitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusHours(2));

        assertTrue(visitIndex.isPresent());
        assertEquals(1, visitIndex.getAsInt());
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
        final OptionalInt visitIndex =
                CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusHours(1).plusMinutes(30));
        assertTrue(visitIndex.isPresent());
        assertEquals(0, visitIndex.getAsInt());
    }

    // Regression test for the review-reported bug: a station's OWN ARRIVAL and DEPARTURE can also share the
    // exact same scheduled_time (a zero-dwell stop, e.g. a scheduled pass-by point). Unlike the
    // cross-station-boundary tie above, a global DEPARTURE-first tie-break here would misfire: since the
    // pairing loop assumes strict ARRIVAL/DEPARTURE alternation, treating TPE's DEPARTURE as sorted first would
    // split TPE into two bogus stops (one with only a departure, mistaken for an origin; one with only an
    // arrival, mistaken for a terminus) instead of one TPE stop with both legs. This is why the tie-break must
    // be station-aware: DEPARTURE-first only when the tied rows are two DIFFERENT stations.
    @Test
    public void of_pairsRowsCorrectlyWhenSameStationArrivalAndDepartureTie() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        // TPE's own ARRIVAL and DEPARTURE tie exactly (zero scheduled dwell time).
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);

        assertEquals(3, stops.size());
        assertEquals("HKI", stops.get(0).departure().stationShortCode);
        // TPE must be a single, full arrival+departure pair - not split into two stops.
        assertEquals("TPE", stops.get(1).arrival().stationShortCode);
        assertTrue(stops.get(1).departure() != null);
        assertEquals("TPE", stops.get(1).departure().stationShortCode);
        assertEquals("OL", stops.get(2).arrival().stationShortCode);
        assertTrue(stops.get(2).departure() == null); // OL is the terminus
    }

    // Regression test for the reviewer's comparator-contract-violation finding: a single tied instant can
    // involve THREE stations at once - HKI's DEPARTURE completing a stay opened earlier, TPE's own ARRIVAL and
    // DEPARTURE tying with each other (zero-dwell), and JY's ARRIVAL opening a stay that continues later. A
    // pairwise "DEPARTURE-first unless same station" comparator is not transitive across this chain (HKI's and
    // JY's rows are different stations so compare "equal" to each other, yet each compares with the opposite
    // sign against TPE's rows) - this used to risk List.sort throwing "Comparison method violates its general
    // contract" or silently misordering. The fix reconstructs the tied run by station instead of by pairwise
    // comparison, so it must still produce HKI(leading)/TPE(zero-dwell pair)/JY(trailing) in order.
    @Test
    public void of_pairsRowsCorrectlyWhenThreeStationsTieAtSameInstant() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        // HKI's own ARRIVAL is scheduled earlier (not part of the tie); its DEPARTURE, TPE's own ARRIVAL and
        // DEPARTURE (zero-dwell), and JY's own ARRIVAL all then share the exact same instant; JY's DEPARTURE is
        // scheduled later (not part of the tie either).
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.ARRIVAL, T0, false, T0));
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("JY", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, T0.plusHours(1)));
        rows.add(row("JY", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);

        // All three stations must come out as full, correctly-paired stops, in the right (causal) order -
        // despite four of their six rows sharing one exact tied instant.
        assertEquals(3, stops.size());
        assertEquals("HKI", stops.get(0).arrival().stationShortCode);
        assertEquals("HKI", stops.get(0).departure().stationShortCode);
        assertEquals("TPE", stops.get(1).arrival().stationShortCode);
        assertEquals("TPE", stops.get(1).departure().stationShortCode);
        assertEquals("JY", stops.get(2).arrival().stationShortCode);
        assertEquals("JY", stops.get(2).departure().stationShortCode);
    }

    // Regression test: a non-commercial stop (e.g. a technical/operational-only stop with no passenger
    // exchange) must be filtered out of the paired stop sequence entirely - mirroring the NeTEx static timetable
    // and SIRI-ET's own CommercialStopRule, which never emit such a stop's own visitIndex either. If it were
    // kept, it would wrongly consume a slot in the pairing/visit-count sequence for the following stations.
    @Test
    public void of_skipsNonCommercialStopEntirely() {
        final List<GTFSTimeTableRow> rows = new ArrayList<>();
        rows.add(row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, T0, false, T0));
        final GTFSTimeTableRow nonCommercialArrival =
                row("XXX", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusMinutes(30), false, T0.plusMinutes(30));
        nonCommercialArrival.commercialStop = false;
        final GTFSTimeTableRow nonCommercialDeparture =
                row("XXX", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusMinutes(31), false, T0.plusMinutes(31));
        nonCommercialDeparture.commercialStop = false;
        rows.add(nonCommercialArrival);
        rows.add(nonCommercialDeparture);
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, T0.plusHours(1), false, null));
        rows.add(row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, T0.plusHours(1).plusMinutes(2), false, null));

        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);

        assertEquals(2, stops.size());
        assertEquals("HKI", stops.get(0).departure().stationShortCode);
        assertEquals("TPE", stops.get(1).arrival().stationShortCode);
        // TPE keeps visitIndex 0 - the skipped non-commercial XXX stop never occupied a slot. "now" sits after
        // XXX's actual completion but before TPE's own (not-yet-elapsed) estimate, matching how the other tests
        // in this class evaluate a still-eligible completing row.
        final OptionalInt tpeVisitIndex = CommercialStopVisits.currentVisitIndex(stops, "TPE", T0.plusMinutes(45));
        assertTrue(tpeVisitIndex.isPresent());
        assertEquals(0, tpeVisitIndex.getAsInt());
    }
}
