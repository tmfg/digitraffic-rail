package fi.livi.rata.avoindata.updater.service.netex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Borrows a platform from another journey that shares this train's stable service identity, for
 * stops that no observation could fill. A journey running only in a future timetable period has no
 * history and no upcoming run, but another journey of the same identity usually does, and platforms
 * rarely differ between them.
 *
 * <p>The identity is the thing that outlives a timetable change:
 * <ul>
 * <li><b>commuter trains</b> key on the line code (I, K, E \u2026); the running number is reassigned
 * every period. A line uses different platforms in each direction, so the key also carries the
 * direction of travel \u2014 derived from a per-line canonical stop order, not the immediate
 * neighbour, so every journey going the same way shares a key even when their exact next stop
 * differs (skip-stops, short turns). It tries, in order, {@code (line, station, direction)}, then
 * {@code (line, station)} across both directions, then {@code (station, direction)} across all
 * lines — the last for a station whose platform is known from another line but not this one. That
 * cross-line direction is only used for lines oriented against a shared anchor (HKI/PSL) so
 * "forward" means the same way for every line.</li>
 * <li><b>every other train</b> keys on train type + number (IC:1, PYO:2), which is itself a single
 * directed service \u2014 so no direction is needed and adding one would fragment the pool.</li>
 * </ul>
 *
 * <p>The pool is built from tracks resolved by the exact sources (upcoming and history) that run
 * before this one, so a borrowed platform is always a real observation of a sibling, never another
 * guess.
 */
@Component
public class IdentityTrackSource {

    /** Fills blank tracks in place and returns how many were filled. */
    public int fill(final List<List<Schedule>> schedulesByKind) {
        final Map<String, LineOrder> lineOrder = buildLineOrders(schedulesByKind);

        final Map<String, Map<String, Integer>> seen = new HashMap<>();
        forEachStop(schedulesByKind, lineOrder, (keys, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                for (final String key : keys) {
                    seen.computeIfAbsent(key, k -> new HashMap<>()).merge(row.commercialTrack, 1, Integer::sum);
                }
            }
        });

        final int[] filled = { 0 };
        forEachStop(schedulesByKind, lineOrder, (keys, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                return;
            }
            // keys run most-specific first: same-direction, then the (line, station) fallback
            for (final String key : keys) {
                final String track = mostUsed(seen.get(key));
                if (track != null) {
                    row.commercialTrack = track;
                    filled[0]++;
                    return;
                }
            }
        });
        return filled[0];
    }

    private void forEachStop(final List<List<Schedule>> schedulesByKind,
            final Map<String, LineOrder> lineOrder,
            final BiConsumer<List<String>, ScheduleRow> action) {
        for (final List<Schedule> schedules : schedulesByKind) {
            for (final Schedule schedule : schedules) {
                final List<ScheduleRow> stops = commercialStops(schedule);
                if (StringUtils.isNotBlank(schedule.commuterLineId)) {
                    final String line = "L:" + schedule.commuterLineId;
                    final LineOrder lo = lineOrder.get(schedule.commuterLineId);
                    for (int i = 0; i < stops.size(); i++) {
                        final String station = stops.get(i).station.stationShortCode;
                        final String dir = lo == null ? null : directionAt(lo.order(), stops, i);
                        final List<String> keys = new ArrayList<>(3);
                        if (dir != null) {
                            keys.add(line + "|" + station + "|" + dir);
                        }
                        keys.add(line + "|" + station);
                        // cross-line last resort: a station's platform known from another line, but
                        // only when this line is oriented against the shared anchor so the direction
                        // means the same for every line
                        if (dir != null && lo != null && lo.anchored()) {
                            keys.add("S:" + station + "|" + dir);
                        }
                        action.accept(keys, stops.get(i));
                    }
                } else {
                    final String ld = longDistanceIdentity(schedule);
                    if (ld == null) {
                        continue;
                    }
                    for (final ScheduleRow row : stops) {
                        action.accept(List.of(ld + "|" + row.station.stationShortCode), row);
                    }
                }
            }
        }
    }

    /** Type + number is a single directed service, so it needs no direction and no fallback. */
    private static String longDistanceIdentity(final Schedule schedule) {
        if (schedule.trainType != null && StringUtils.isNotBlank(schedule.trainType.name)
                && schedule.trainNumber != null) {
            return "T:" + schedule.trainType.name + ":" + schedule.trainNumber;
        }
        return null;
    }

    /**
     * The longest commercial-stop sequence a line runs, used as its canonical order, oriented so a
     * shared anchor (HKI/PSL) sits near the start. That makes "forward" mean the same direction for
     * every anchored line, so the {@code (station, direction)} cross-line tier is comparable. A
     * journey travelling the other way visits the same stations in decreasing order.
     */
    private Map<String, LineOrder> buildLineOrders(final List<List<Schedule>> schedulesByKind) {
        final Map<String, List<String>> longest = new HashMap<>();
        for (final List<Schedule> schedules : schedulesByKind) {
            for (final Schedule schedule : schedules) {
                if (StringUtils.isBlank(schedule.commuterLineId)) {
                    continue;
                }
                final List<String> seq = new ArrayList<>();
                for (final ScheduleRow row : commercialStops(schedule)) {
                    seq.add(row.station.stationShortCode);
                }
                longest.merge(schedule.commuterLineId, seq, (a, b) -> b.size() > a.size() ? b : a);
            }
        }
        final Map<String, LineOrder> orders = new HashMap<>();
        for (final Map.Entry<String, List<String>> entry : longest.entrySet()) {
            final List<String> seq = new ArrayList<>(entry.getValue());
            final int anchor = anchorIndex(seq);
            if (anchor >= 0 && anchor * 2 > seq.size()) {
                Collections.reverse(seq);
            }
            orders.put(entry.getKey(), new LineOrder(seq, anchor >= 0));
        }
        return orders;
    }

    /** Position of the first shared anchor station, or -1 when the line touches neither. */
    private static int anchorIndex(final List<String> seq) {
        int best = -1;
        for (final String anchor : List.of("HKI", "PSL")) {
            final int idx = seq.indexOf(anchor);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    /**
     * "F" or "B" for travel along or against the line's canonical order, or null when the order
     * cannot place this stop (then only the direction-less fallback key applies).
     */
    private static String directionAt(final List<String> order, final List<ScheduleRow> stops, final int index) {
        if (order == null) {
            return null;
        }
        final int here = order.indexOf(stops.get(index).station.stationShortCode);
        if (here < 0) {
            return null;
        }
        if (index + 1 < stops.size()) {
            final int next = order.indexOf(stops.get(index + 1).station.stationShortCode);
            if (next >= 0) {
                return next > here ? "F" : "B";
            }
        }
        if (index > 0) {
            final int prev = order.indexOf(stops.get(index - 1).station.stationShortCode);
            if (prev >= 0) {
                return prev < here ? "F" : "B";
            }
        }
        return null;
    }

    private static List<ScheduleRow> commercialStops(final Schedule schedule) {
        final List<ScheduleRow> stops = new ArrayList<>();
        for (final ScheduleRow row : schedule.scheduleRows) {
            if (CommercialStopRule.isCommercialStop(row)) {
                stops.add(row);
            }
        }
        return stops;
    }

    /** Ties break on the track name so the same input always yields the same feed. */
    private static String mostUsed(final Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return null;
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** A line's canonical stop order, oriented toward the shared anchor when it touches one. */
    private record LineOrder(List<String> order, boolean anchored) {
    }
}
