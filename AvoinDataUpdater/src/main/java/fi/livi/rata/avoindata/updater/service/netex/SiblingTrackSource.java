package fi.livi.rata.avoindata.updater.service.netex;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Track for stops that have never been observed at all. A journey running only in a
 * future timetable period has no history and no upcoming run, but another journey
 * over the same stations usually does, and platforms rarely differ between them.
 */
@Component
public class SiblingTrackSource {

    private final NeTExRouteService routeService;

    public SiblingTrackSource(final NeTExRouteService routeService) {
        this.routeService = routeService;
    }

    /** Fills blank tracks in place and returns how many were filled. */
    public int fill(final List<List<Schedule>> schedulesByKind) {
        final Map<String, Map<String, Map<String, Integer>>> seen = new HashMap<>();
        forEachCommercialStop(schedulesByKind, (routeKey, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                seen.computeIfAbsent(routeKey, k -> new HashMap<>())
                        .computeIfAbsent(row.station.stationShortCode, k -> new HashMap<>())
                        .merge(row.commercialTrack, 1, Integer::sum);
            }
        });

        final int[] filled = { 0 };
        forEachCommercialStop(schedulesByKind, (routeKey, row) -> {
            if (StringUtils.isNotBlank(row.commercialTrack)) {
                return;
            }
            final var perStation = seen.get(routeKey);
            final String track = perStation == null ? null
                    : mostUsed(perStation.get(row.station.stationShortCode));
            if (track != null) {
                row.commercialTrack = track;
                filled[0]++;
            }
        });
        return filled[0];
    }

    private void forEachCommercialStop(final List<List<Schedule>> schedulesByKind,
            final java.util.function.BiConsumer<String, ScheduleRow> action) {
        for (final List<Schedule> schedules : schedulesByKind) {
            for (final Schedule schedule : schedules) {
                final String routeKey = routeService.computeStationHash(
                        routeService.extractCommercialStopsWithTrack(schedule));
                for (final ScheduleRow row : schedule.scheduleRows) {
                    if (CommercialStopRule.isCommercialStop(row)) {
                        action.accept(routeKey, row);
                    }
                }
            }
        }
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
}
