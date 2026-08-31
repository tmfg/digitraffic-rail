package fi.livi.rata.avoindata.updater.service.netex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Derives Routes and JourneyPatterns from schedule data.
 * Deduplicates patterns for trains sharing the same stop sequence.
 */
@Service
public class NeTExRouteService {

    private final NeTExIdGenerator idGenerator;

    public NeTExRouteService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Computes a track-qualified hash from station+track tuples.
     * Format: "HKI-4_TPE-1_OL-2" — '-' joins station to track, '_' separates
     * stops. When track is null, station only.
     */
    public String computeTrackQualifiedHash(final List<StopWithTrack> stopsWithTrack) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stopsWithTrack.size(); i++) {
            if (i > 0) {
                sb.append("_");
            }
            final StopWithTrack s = stopsWithTrack.get(i);
            sb.append(s.stationShortCode());
            if (s.commercialTrack() != null && !s.commercialTrack().isBlank()) {
                sb.append("-").append(s.commercialTrack());
            }
        }
        return sb.toString();
    }

    /**
     * Creates routes and journey patterns with track-qualified SSP references.
     * Trains with identical (station+track) sequences share a JourneyPattern.
     * Route points remain station-level (not track-qualified).
     */
    public NeTExRouteData createRouteDataTrackAware(final List<Schedule> schedules) {
        final Map<String, NeTExRouteData.NeTExRoute> routeMap = new LinkedHashMap<>();
        final Map<String, NeTExRouteData.NeTExJourneyPattern> patternMap = new LinkedHashMap<>();
        final Map<Long, String> scheduleToPatternId = new HashMap<>();

        for (final Schedule schedule : schedules) {
            final List<StopWithTrack> commercialStopsWithTrack = extractCommercialStopsWithTrack(schedule);
            if (commercialStopsWithTrack.isEmpty()) {
                continue;
            }
            final String hash = computeTrackQualifiedHash(commercialStopsWithTrack);
            final String lineIdentifier = deriveLineIdentifier(schedule);
            final String patternId = idGenerator.journeyPatternId(lineIdentifier, hash);

            scheduleToPatternId.put(schedule.id, patternId);

            if (!patternMap.containsKey(patternId)) {
                final String routeId = idGenerator.routeId(lineIdentifier, hash);

                routeMap.put(routeId, buildRoute(lineIdentifier, routeId, commercialStopsWithTrack));
                patternMap.put(patternId, buildJourneyPattern(patternId, routeId, commercialStopsWithTrack));
            }
        }

        return new NeTExRouteData(
                new ArrayList<>(routeMap.values()),
                new ArrayList<>(patternMap.values()),
                scheduleToPatternId);
    }

    /**
     * Non-stop operating points are left out: the Nordic profile requires every
     * RoutePoint to project onto a ScheduledStopPoint, which a passing point has
     * no business having.
     */
    private NeTExRouteData.NeTExRoute buildRoute(final String lineIdentifier, final String routeId,
            final List<StopWithTrack> commercialStopsWithTrack) {
        final String lineRef = idGenerator.lineId(lineIdentifier);
        final List<String> stationCodes = commercialStopsWithTrack.stream()
                .map(StopWithTrack::stationShortCode)
                .toList();
        final String routeName = stationCodes.get(0) + " - " + stationCodes.get(stationCodes.size() - 1);
        final List<String> routePointRefs = stationCodes.stream()
                .map(idGenerator::routePointId)
                .collect(Collectors.toList());
        return new NeTExRouteData.NeTExRoute(routeId, routeName, lineRef, routePointRefs);
    }

    private NeTExRouteData.NeTExJourneyPattern buildJourneyPattern(final String patternId, final String routeId,
            final List<StopWithTrack> commercialStopsWithTrack) {
        final List<NeTExRouteData.NeTExStopPointInPattern> stopPoints = new ArrayList<>();
        final String lastStopCode = commercialStopsWithTrack.get(commercialStopsWithTrack.size() - 1)
                .stationShortCode();
        for (int i = 0; i < commercialStopsWithTrack.size(); i++) {
            final StopWithTrack swt = commercialStopsWithTrack.get(i);
            final boolean isFirst = (i == 0);
            final boolean isLast = (i == commercialStopsWithTrack.size() - 1);
            final String destRef = isFirst ? idGenerator.destinationDisplayId(lastStopCode) : null;
            final String sspRef = idGenerator.scheduledStopPointId(swt.stationShortCode(), swt.commercialTrack());
            stopPoints.add(new NeTExRouteData.NeTExStopPointInPattern(i + 1, sspRef, !isLast, !isFirst, destRef));
        }
        return new NeTExRouteData.NeTExJourneyPattern(patternId, routeId, stopPoints);
    }

    /**
     * A (station, track) pair for journey pattern hash computation.
     */
    public record StopWithTrack(String stationShortCode, String commercialTrack) {
    }

    private List<StopWithTrack> extractCommercialStopsWithTrack(final Schedule schedule) {
        final List<StopWithTrack> stops = new ArrayList<>();
        for (final ScheduleRow row : schedule.scheduleRows) {
            if (isCommercialStop(row)) {
                stops.add(new StopWithTrack(row.station.stationShortCode, row.commercialTrack));
            }
        }
        return stops;
    }

    private boolean isCommercialStop(final ScheduleRow row) {
        // First stop (no arrival) and last stop (no departure) are always commercial
        if (row.arrival == null || row.departure == null) {
            return true;
        }
        if (row.departure.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL) {
            return true;
        }
        return row.arrival.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL;
    }

    private String deriveLineIdentifier(final Schedule schedule) {
        if (schedule.commuterLineId != null && !schedule.commuterLineId.isEmpty()) {
            return schedule.commuterLineId;
        }
        return schedule.trainType.name + "-" + schedule.trainNumber;
    }
}
