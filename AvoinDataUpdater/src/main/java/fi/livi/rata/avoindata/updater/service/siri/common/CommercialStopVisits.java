package fi.livi.rata.avoindata.updater.service.siri.common;

import java.time.ZonedDateTime;
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

        /**
         * Mirrors the live-location query's own eligibility test for its {@code next} row (see
         * {@code GTFSTrainRepository.getTrainLocations}: {@code actual_time is null and live_estimate_time >
         * CURRENT_TIMESTAMP()}), applied to {@link #completingRow()}. A completing row with no actual time yet
         * but a <em>stale</em> estimate (no longer in the future - e.g. delay data hasn't refreshed) is not
         * eligible there either: the SQL query skips straight past such a row to the next one that qualifies,
         * so a repeated-station visit stuck in that state must not be reported as current here, or {@link
         * #currentVisitIndex} would return the wrong (earlier, already-superseded) occurrence.
         */
        private boolean isCompletingRowEligible(final ZonedDateTime now) {
            final GTFSTimeTableRow row = completingRow();
            return row.actualTime == null && row.liveEstimateTime != null && row.liveEstimateTime.isAfter(now);
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
        // type descending breaks a same-instant tie in favor of DEPARTURE, matching
        // GTFSTrainRepository.getTrainLocations's next lateral: this only matters when the current station's
        // DEPARTURE and the next station's ARRIVAL share the same scheduled_time (zero scheduled transit time,
        // a real occurrence - see TrainFactory's TPE DEPARTURE / JY ARRIVAL fixture rows). Sorting ARRIVAL-first
        // on such a tie would let a different station's ARRIVAL slot in between this station's ARRIVAL/DEPARTURE
        // pair below, corrupting the pairing (and every subsequent visitIndex) from that point on.
        ordered.sort(Comparator.comparing((GTFSTimeTableRow r) -> r.scheduledTime)
                .thenComparing(r -> r.type, Comparator.reverseOrder()));

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
     * that query's {@code actual_time is null and live_estimate_time > CURRENT_TIMESTAMP()} SQL filter (see
     * {@code now}, and {@link Stop#isCompletingRowEligible}, for how that filter is mirrored here). When a
     * station is served more than once, an earlier visit is only skipped once its completing row (see {@link
     * Stop#completingRow()}) is no longer eligible — either it already has an actual time, or its estimate has
     * gone stale (no longer in the future) — so the first still-eligible match is unambiguous and never an
     * occurrence the live query has itself already moved past. Cancelled visits still occupy a slot in the
     * running count (so a later, real visit keeps its true visitIndex — same convention as {@code
     * EtJourneyInterpreter}), but are never themselves returned as the current occurrence: the live-location
     * query excludes cancelled rows outright (its {@code cancelled is false} filter), and a cancelled stop's
     * completing row typically never gets an actual time either (it's never actually run), so without this
     * exclusion a cancelled visit would be wrongly reported as current instead of the next, real visit. Empty
     * when the station never occurs, or every (non-cancelled) occurrence is already completed/stale (should not
     * normally happen for a station a live location still reports) — except the arrived-terminus case below.
     *
     * <p>Terminus fallback: mirrors {@code GTFSTrainRepository.getTrainLocations}'s {@code term} lateral join.
     * A terminus has only an ARRIVAL row (no departure), so once that arrival's actual time is set (train has
     * arrived), there is no later not-yet-completed row left to match for it — the loop above would find
     * nothing and this stop's live location would silently lose its {@code MonitoredCall}/{@code
     * VehicleAtStop} when its live track is unknown. Since the arrived terminus is by definition the last
     * (non-cancelled) stop in the journey, it is still reported as current here, exactly as {@code term}
     * reports it in the live-location query.
     *
     * @param now the time to evaluate estimate staleness against - callers should pass the same instant used to
     *            build the live locations being resolved (see {@code SiriVmGenerationService}), matching the
     *            SQL's own {@code CURRENT_TIMESTAMP()}.
     */
    public static OptionalInt currentVisitIndex(final List<Stop> stops, final String stationShortCode,
                                                final ZonedDateTime now) {
        final Map<String, Integer> visitCounts = new HashMap<>();
        for (final Stop stop : stops) {
            final String station = stop.representative().stationShortCode;
            // 0-based occurrence of this station within the journey so far, incremented on every visit
            // regardless of whether it is the one being searched for (see EtJourneyInterpreter for the same
            // counting pattern).
            final int visitIndex = visitCounts.merge(station, 1, Integer::sum) - 1;
            if (station.equals(stationShortCode) && !stop.isCancelled() && stop.isCompletingRowEligible(now)) {
                return OptionalInt.of(visitIndex);
            }
        }
        for (int i = stops.size() - 1; i >= 0; i--) {
            final Stop stop = stops.get(i);
            if (stop.isCancelled()) {
                continue;
            }
            final String station = stop.representative().stationShortCode;
            if (station.equals(stationShortCode) && stop.departure == null) {
                return OptionalInt.of(visitCounts.get(station) - 1);
            }
            break;
        }
        return OptionalInt.empty();
    }
}
