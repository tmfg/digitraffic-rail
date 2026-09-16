package fi.livi.rata.avoindata.updater.service.siri.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.apache.commons.lang3.BooleanUtils;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule.Leg;

/**
 * Pairs a train's raw {@link GTFSTimeTableRow} rows into its ordered commercial-stop sequence and locates a
 * specific station's <em>current</em> (first not-yet-completed) visit within it. This 0-based visitIndex is the
 * same key used by the persisted {@code NeTExPublishedJourneyTrack} / {@link
 * fi.livi.rata.avoindata.updater.service.siri.et.PlannedTrackLookup}, so a live stop resolved from a single row
 * (which carries no visitIndex of its own, e.g. SIRI-VM's {@code GTFSTrainLocation}) can be matched back to the
 * right planned track when a station is served more than once.
 *
 * <p>Mirrors the pairing/commercial-filter rules SIRI-ET's {@code EtJourneyInterpreter} applies to a train's full
 * row list. Kept as a separate, narrower utility (rather than shared directly with that class) since SIRI-VM only
 * needs this one lookup — an on-demand fallback for the rare "live track unknown" case (see {@code
 * VmJourneyConverter}), not the full call-by-call conversion ET performs on every row.
 */
public final class CommercialStopVisits {

    private CommercialStopVisits() {
    }

    /** One commercial stop: an origin has {@code arrival == null}, a terminus has {@code departure == null}. */
    public record Stop(GTFSTimeTableRow arrival, GTFSTimeTableRow departure) {

        private GTFSTimeTableRow representative() {
            return arrival != null ? arrival : departure;
        }

        /**
         * The row whose actual time means the train has fully finished this stop: the departure, or — at a
         * terminus with no departure — the arrival itself. A stop with both rows is still "current" while
         * dwelling (arrival already actual, departure not), so the departure is what must be checked, not
         * whichever row happens to be non-null first.
         */
        private GTFSTimeTableRow completingRow() {
            return departure != null ? departure : arrival;
        }

        /** Mirrors {@code EtJourneyInterpreter}'s cancellation check: a stop is cancelled if either of its rows
         * is. The live-location query (see {@code GTFSTrainRepository.getTrainLocations}) excludes cancelled
         * rows outright, so a cancelled stop can never be the one it resolves - {@link #currentVisitIndex}
         * mirrors that by never selecting a cancelled stop as current either, even when its completing row
         * happens to have no actual time (the usual case, since a cancelled stop is never actually run). */
        private boolean isCancelled() {
            if (arrival != null && arrival.cancelled) {
                return true;
            }
            return departure != null && departure.cancelled;
        }
    }

    /** Pairs {@code rows} into stops (see {@link Stop}) and keeps only the commercial ones, in schedule order. */
    public static List<Stop> of(final List<GTFSTimeTableRow> rows) {
        final List<Stop> stops = new ArrayList<>();
        if (rows.isEmpty()) {
            return stops;
        }

        final List<GTFSTimeTableRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing((GTFSTimeTableRow r) -> r.scheduledTime).thenComparing(r -> r.type));

        int i = 0;
        if (ordered.get(0).type == TimeTableRow.TimeTableRowType.DEPARTURE) {
            addIfCommercial(stops, null, ordered.get(0));
            i = 1;
        }

        while (i < ordered.size()) {
            final GTFSTimeTableRow arrival = ordered.get(i);
            i++;
            if (i < ordered.size() && ordered.get(i).type == TimeTableRow.TimeTableRowType.DEPARTURE) {
                addIfCommercial(stops, arrival, ordered.get(i));
                i++;
            } else {
                addIfCommercial(stops, arrival, null);
            }
        }

        return stops;
    }

    private static void addIfCommercial(final List<Stop> stops, final GTFSTimeTableRow arrival,
                                        final GTFSTimeTableRow departure) {
        // Must select the same stops as the NeTEx timetable / SIRI-ET — see CommercialStopRule.
        if (CommercialStopRule.isCommercialStop(leg(arrival), leg(departure))) {
            stops.add(new Stop(arrival, departure));
        }
    }

    private static Leg leg(final GTFSTimeTableRow row) {
        if (row == null) {
            return Leg.ABSENT;
        }
        return BooleanUtils.isTrue(row.commercialStop) ? Leg.COMMERCIAL : Leg.NON_COMMERCIAL;
    }

    /**
     * The 0-based occurrence of the first not-yet-completed commercial stop at {@code stationShortCode} within
     * {@code stops} (schedule order) — the same stop a live "next station" query resolves (see
     * {@code GTFSTrainRepository.getTrainLocations}), found here by scanning the full planned order instead of
     * that query's {@code actual_time is null} SQL filter. When a station is served more than once, an earlier
     * visit is only skipped once its completing row (see {@link Stop#completingRow()}) already has an actual
     * time, so the first not-yet-completed match is unambiguous. Cancelled visits still occupy a slot in the
     * running count (so a later, real visit keeps its true visitIndex — same convention as {@code
     * EtJourneyInterpreter}), but are never themselves returned as the current occurrence: the live-location
     * query excludes cancelled rows outright (its {@code cancelled is false} filter), and a cancelled stop's
     * completing row typically never gets an actual time either (it's never actually run), so without this
     * exclusion a cancelled visit would be wrongly reported as current instead of the next, real visit. Empty
     * when the station never occurs, or every (non-cancelled) occurrence is already completed (should not
     * normally happen for a station a live location still reports).
     */
    public static OptionalInt currentVisitIndex(final List<Stop> stops, final String stationShortCode) {
        final Map<String, Integer> visitCounts = new HashMap<>();
        for (final Stop stop : stops) {
            final String station = stop.representative().stationShortCode;
            // 0-based occurrence of this station within the journey so far, incremented on every visit
            // regardless of whether it is the one being searched for (see EtJourneyInterpreter for the same
            // counting pattern).
            final int visitIndex = visitCounts.merge(station, 1, Integer::sum) - 1;
            if (station.equals(stationShortCode) && !stop.isCancelled() && stop.completingRow().actualTime == null) {
                return OptionalInt.of(visitIndex);
            }
        }
        return OptionalInt.empty();
    }
}
