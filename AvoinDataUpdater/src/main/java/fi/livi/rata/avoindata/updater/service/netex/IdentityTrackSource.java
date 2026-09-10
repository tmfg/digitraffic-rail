package fi.livi.rata.avoindata.updater.service.netex;

import java.util.ArrayList;
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
 * <li><b>commuter trains</b> key on the line code (I, K, E \u2026). The running number is reassigned
 * every period, so it cannot be used. A line runs both directions, so the neighbouring stop is part
 * of the key to keep the two apart.</li>
 * <li><b>every other train</b> keys on train type + number (IC:1, PYO:2), which is itself a single
 * directed service \u2014 so the neighbour is not needed and would only fragment the pool on
 * skip-stop days.</li>
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
        final Map<StopKey, Map<String, Integer>> seen = new HashMap<>();
        forEachStop(schedulesByKind, (key, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                seen.computeIfAbsent(key, k -> new HashMap<>()).merge(row.commercialTrack, 1, Integer::sum);
            }
        });

        final int[] filled = { 0 };
        forEachStop(schedulesByKind, (key, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                return;
            }
            final String track = mostUsed(seen.get(key));
            if (track != null) {
                row.commercialTrack = track;
                filled[0]++;
            }
        });
        return filled[0];
    }

    private void forEachStop(final List<List<Schedule>> schedulesByKind,
            final BiConsumer<StopKey, ScheduleRow> action) {
        for (final List<Schedule> schedules : schedulesByKind) {
            for (final Schedule schedule : schedules) {
                final String identity = identityOf(schedule);
                if (identity == null) {
                    continue;
                }
                final boolean directed = StringUtils.isNotBlank(schedule.commuterLineId);
                final List<ScheduleRow> stops = commercialStops(schedule);
                for (int i = 0; i < stops.size(); i++) {
                    final String neighbour = directed ? neighbourOf(stops, i) : "";
                    if (neighbour != null) {
                        action.accept(new StopKey(identity, stops.get(i).station.stationShortCode, neighbour),
                                stops.get(i));
                    }
                }
            }
        }
    }

    /**
     * Commuter trains key on the line code, which survives the periodic renumbering; every other
     * train keys on type + number, which is itself a single directed service. A train with neither
     * has no stable identity to borrow from.
     */
    private static String identityOf(final Schedule schedule) {
        if (StringUtils.isNotBlank(schedule.commuterLineId)) {
            return "L:" + schedule.commuterLineId;
        }
        if (schedule.trainType != null && StringUtils.isNotBlank(schedule.trainType.name)
                && schedule.trainNumber != null) {
            return "T:" + schedule.trainType.name + ":" + schedule.trainNumber;
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

    /** Where the journey goes next, or where it came from at the last stop. */
    private static String neighbourOf(final List<ScheduleRow> stops, final int index) {
        if (index + 1 < stops.size()) {
            return ">" + stops.get(index + 1).station.stationShortCode;
        }
        return index > 0 ? "<" + stops.get(index - 1).station.stationShortCode : null;
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

    private record StopKey(String identity, String stationShortCode, String neighbour) {
    }
}
