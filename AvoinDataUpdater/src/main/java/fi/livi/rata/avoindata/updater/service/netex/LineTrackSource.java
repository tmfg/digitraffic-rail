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
 * Borrows a platform from another journey of the same commuter line.
 *
 * <p>A commuter train number is reassigned at every timetable change, so a service starting next
 * month shares no number with anything that has ever run and no earlier source can answer it. The
 * line (I, K, E …) outlives the numbering, which makes it the only stable thing to match on.
 *
 * <p>The neighbouring stop is part of the key because a line uses different platforms in each
 * direction, and the two senses are marked apart so a terminus cannot borrow from a through stop.
 */
@Component
public class LineTrackSource {

    public int fill(final List<List<Schedule>> schedulesByKind) {
        final Map<StopKey, Map<String, Integer>> seen = new HashMap<>();
        forEachDirectedStop(schedulesByKind, (key, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                seen.computeIfAbsent(key, k -> new HashMap<>()).merge(row.commercialTrack, 1, Integer::sum);
            }
        });

        final int[] filled = { 0 };
        forEachDirectedStop(schedulesByKind, (key, row) -> {
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

    private void forEachDirectedStop(final List<List<Schedule>> schedulesByKind,
            final BiConsumer<StopKey, ScheduleRow> action) {
        for (final List<Schedule> schedules : schedulesByKind) {
            for (final Schedule schedule : schedules) {
                if (StringUtils.isBlank(schedule.commuterLineId)) {
                    continue;
                }
                final List<ScheduleRow> stops = commercialStops(schedule);
                for (int i = 0; i < stops.size(); i++) {
                    final String neighbour = neighbourOf(stops, i);
                    if (neighbour != null) {
                        action.accept(new StopKey(schedule.commuterLineId,
                                stops.get(i).station.stationShortCode, neighbour), stops.get(i));
                    }
                }
            }
        }
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

    private record StopKey(String commuterLineId, String stationShortCode, String neighbour) {
    }
}
