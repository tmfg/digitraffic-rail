package fi.livi.rata.avoindata.updater.service.siri.common;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
         * Mirrors the live-location query's own eligibility test for its {@code next} row (see
         * {@code GTFSTrainRepository.getTrainLocations}: {@code commercial_stop is true and cancelled is false
         * and actual_time is null and live_estimate_time > CURRENT_TIMESTAMP()}), applied independently to
         * <em>either</em> leg of this stop. The SQL query selects the single earliest not-yet-happened row
         * train-wide regardless of whether it is an ARRIVAL or a DEPARTURE: while a train is still
         * <em>approaching</em> a stop, only its ARRIVAL row typically carries a live estimate yet (the
         * DEPARTURE hasn't been reached, let alone estimated) - checking only the departure (the usual
         * "completing" row for a non-terminus stop) would then find no estimate and wrongly report this stop as
         * not current/not eligible, even though the SQL query has already resolved it via that very ARRIVAL
         * row. The *dwelling* case (ARRIVAL already actual, DEPARTURE pending) is still covered by checking
         * either leg, since the ARRIVAL is then no longer eligible (actual_time set) and only the DEPARTURE can
         * be. A row with no actual time yet but a <em>stale</em> estimate (no longer in the future - e.g. delay
         * data hasn't refreshed) is not eligible either: the SQL query skips straight past such a row to the
         * next one that qualifies, so a repeated-station visit stuck in that state must not be reported as
         * current here, or {@link #currentVisitIndex} would return the wrong (earlier, already-superseded)
         * occurrence.
         *
         * <p>Crucially, the {@code cancelled}/{@code commercial_stop} filters are per-row in the SQL, exactly
         * like the other filters above - not applied to the paired stop as a whole. A cancelled or
         * non-commercial ARRIVAL simply drops out of the SQL's candidate window; it does not disqualify a
         * perfectly eligible DEPARTURE at the very same stop (see {@code
         * GTFSTrainRepositoryTest#getTrainLocationsCancelledArrivalTreatedAsNoPairedArrival}, where the SQL
         * selects PSL's active DEPARTURE even though PSL's own ARRIVAL was cancelled). So {@link
         * #isRowEligible} checks cancellation/commercial-stop on each row independently, and this stop is
         * eligible as soon as either row - on its own - passes every filter.
         */
        private boolean isEligible(final ZonedDateTime now) {
            return isRowEligible(arrival, now) || isRowEligible(departure, now);
        }

        private static boolean isRowEligible(final GTFSTimeTableRow row, final ZonedDateTime now) {
            return row != null && !row.cancelled && BooleanUtils.isTrue(row.commercialStop) && row.actualTime == null
                    && row.liveEstimateTime != null && row.liveEstimateTime.isAfter(now);
        }

        /** Mirrors {@code EtJourneyInterpreter}'s cancellation check: a stop is cancelled if either of its rows
         * is. Only used by the arrived-terminus fallback in {@link #currentVisitIndex} (a single-row, ARRIVAL-
         * only case there); the general "is this stop currently reportable" question is answered per-row by
         * {@link #isEligible}/{@link #isRowEligible} instead, which already excludes a cancelled row from
         * eligibility on its own, without disqualifying the other, non-cancelled leg of the same stop. */
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

        final List<GTFSTimeTableRow> ordered = orderRows(rows);

        int i = 0;
        if (ordered.getFirst().type == TimeTableRow.TimeTableRowType.DEPARTURE) {
            addIfCommercial(stops, null, ordered.getFirst());
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

    /// Orders `rows` by scheduled time for the pairing loop above, which assumes rows strictly alternate
    /// ARRIVAL, DEPARTURE, ARRIVAL, DEPARTURE, ... Same-instant ties cannot be resolved with a plain pairwise
    /// tie-break comparator: an earlier version tried "DEPARTURE before ARRIVAL, unless same station" via
    /// `Comparator`, but that is not transitive (two DEPARTUREs at different stations compare equal to each
    /// other, yet each compares with the *opposite* sign against an ARRIVAL at one of those stations) -
    /// `List.sort` can throw "Comparator... violates its general contract" or silently misorder a
    /// three-or-more-row tie. So ties are instead resolved with a dedicated post-processing pass
    /// ({@link #reorderTiedGroup}) that reconstructs each same-instant run explicitly by station, rather than by
    /// a per-pair comparison rule.
    ///
    /// Whether this level of care is actually needed is debatable: a 90-day production-data check found zero
    /// occurrences of even the simplest cross-station tie (see `GTFSTrainRepositoryTest` history/PR discussion),
    /// so real timetables appear to always allot at least a minute of scheduled transit time between stations.
    /// This is arguably overkill for data that doesn't occur in practice - but a `Comparator` that violates its
    /// contract is a latent bug regardless of today's data, and the fix costs nothing at runtime (still a single
    /// linear pass), so it seemed better to spend a bit more code now than to debug a `Comparison method
    /// violates its general contract!` crash later if a future data source or edge case ever does produce one.
    private static List<GTFSTimeTableRow> orderRows(final List<GTFSTimeTableRow> rows) {
        final List<GTFSTimeTableRow> byTime = new ArrayList<>(rows);
        byTime.sort(Comparator.comparing((GTFSTimeTableRow r) -> r.scheduledTime));

        final List<GTFSTimeTableRow> result = new ArrayList<>(byTime.size());
        int i = 0;
        while (i < byTime.size()) {
            int j = i + 1;
            while (j < byTime.size() && byTime.get(j).scheduledTime.equals(byTime.get(i).scheduledTime)) {
                j++;
            }
            result.addAll(reorderTiedGroup(byTime.subList(i, j)));
            i = j;
        }
        return result;
    }

    /// Reconstructs the correct order of a run of rows sharing the exact same scheduled time, by station rather
    /// than by a global tie-break rule. Physically, a train visits stations one at a time, so such a run is
    /// always a single chain: at most one leading row completing a stay opened before the tie (a lone
    /// DEPARTURE, its own ARRIVAL scheduled earlier), zero or more stations fully contained in the tie (a
    /// zero-dwell stop: both its ARRIVAL and DEPARTURE share this same instant), and at most one trailing row
    /// opening a stay that continues after the tie (a lone ARRIVAL, its own DEPARTURE scheduled later). Ordering
    /// as `[leading][zero-dwell pairs, each ARRIVAL before its own DEPARTURE][trailing]` keeps every station's
    /// own rows correctly paired regardless of how many stations happen to tie at once - unlike a global
    /// type-based tie-break, this never depends on comparing one station's row against a different station's
    /// row.
    ///
    /// **Example**: HKI's own ARRIVAL is scheduled earlier (not part of any tie); its DEPARTURE, TPE's own
    /// ARRIVAL and DEPARTURE (a zero-dwell stop), and TKU's own ARRIVAL all share one exact scheduled time;
    /// TKU's DEPARTURE is scheduled later (not part of the tie either):
    ///
    /// | step | value |
    /// |---|---|
    /// | input group (order as received, i.e. arbitrary) | TKU ARR, HKI DEP, TPE DEP, TPE ARR |
    /// | grouped by station | HKI -> [DEP]&nbsp;&nbsp;&nbsp;TPE -> [DEP, ARR]&nbsp;&nbsp;&nbsp;TKU -> [ARR] |
    /// | classified | HKI leading&nbsp;&nbsp;&nbsp;TPE middle (sorted ARR, DEP)&nbsp;&nbsp;&nbsp;TKU trailing |
    /// | result | HKI DEP, TPE ARR, TPE DEP, TKU ARR |
    ///
    /// The caller ({@link #orderRows}) then places this result between HKI's earlier ARRIVAL and TKU's later
    /// DEPARTURE, yielding the fully-correct sequence `HKI ARR, HKI DEP, TPE ARR, TPE DEP, TKU ARR, TKU DEP` -
    /// i.e. 3 clean stops (HKI, TPE, TKU) even though 4 of the 6 rows tie on scheduled time.
    private static List<GTFSTimeTableRow> reorderTiedGroup(final List<GTFSTimeTableRow> group) {
        if (group.size() == 1) {
            return group;
        }

        final Map<String, List<GTFSTimeTableRow>> byStation = new LinkedHashMap<>();
        for (final GTFSTimeTableRow row : group) {
            byStation.computeIfAbsent(row.stationShortCode, k -> new ArrayList<>()).add(row);
        }

        final List<GTFSTimeTableRow> leading = new ArrayList<>();
        final List<GTFSTimeTableRow> middle = new ArrayList<>();
        final List<GTFSTimeTableRow> trailing = new ArrayList<>();
        for (final List<GTFSTimeTableRow> stationRows : byStation.values()) {
            if (stationRows.size() >= 2) {
                stationRows.sort(Comparator.comparing(r -> r.type)); // ARRIVAL(0) before DEPARTURE(1)
                middle.addAll(stationRows);
                continue;
            }
            final GTFSTimeTableRow onlyRow = stationRows.getFirst();
            if (onlyRow.type == TimeTableRow.TimeTableRowType.DEPARTURE) {
                leading.add(onlyRow);
            } else {
                trailing.add(onlyRow);
            }
        }

        final List<GTFSTimeTableRow> ordered = new ArrayList<>(group.size());
        ordered.addAll(leading);
        ordered.addAll(middle);
        ordered.addAll(trailing);
        return ordered;
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
     * that query's {@code commercial_stop is true and cancelled is false and actual_time is null and
     * live_estimate_time > CURRENT_TIMESTAMP()} SQL filter (see {@code now}, and {@link Stop#isEligible}, for
     * how that filter is mirrored here - checking either leg of the stop independently, since the SQL query
     * resolves whichever single row, ARRIVAL or DEPARTURE, is earliest, and applies every filter, cancellation
     * and commercial-stop included, per row rather than to the paired stop as a whole). When a station is
     * served more than once, an earlier visit is only skipped once neither of its rows (see {@link
     * Stop#isEligible}) is eligible anymore — each already has an actual time, is cancelled or non-commercial,
     * or its estimate has gone stale (no longer in the future) — so the first still-eligible match is
     * unambiguous and never an occurrence the live query has itself already moved past. Empty when the station
     * never occurs, or every occurrence is already completed/stale/cancelled (should not normally happen for a
     * station a live location still reports) — except the arrived-terminus case below.
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
            if (station.equals(stationShortCode) && stop.isEligible(now)) {
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
