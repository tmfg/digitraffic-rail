package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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
     * Creates routes and journey patterns from the given schedules.
     * Trains with identical commercial stop sequences share a JourneyPattern.
     */
    public NeTExRouteData createRouteData(final List<Schedule> schedules) {
        final Map<String, NeTExRouteData.NeTExRoute> routeMap = new LinkedHashMap<>();
        final Map<String, NeTExRouteData.NeTExJourneyPattern> patternMap = new LinkedHashMap<>();
        final Map<Long, String> scheduleToPatternId = new HashMap<>();

        for (final Schedule schedule : schedules) {
            final List<String> commercialStops = extractCommercialStopCodes(schedule);
            if (commercialStops.isEmpty()) {
                continue;
            }
            final String hash = computeStopSequenceHash(commercialStops);
            final String lineIdentifier = deriveLineIdentifier(schedule);
            final String patternId = idGenerator.journeyPatternId(lineIdentifier, hash);

            scheduleToPatternId.put(schedule.id, patternId);

            if (!patternMap.containsKey(patternId)) {
                final String lineRef = idGenerator.lineId(lineIdentifier);
                final List<String> allStopCodes = schedule.scheduleRows.stream()
                        .map(row -> row.station.stationShortCode)
                        .collect(Collectors.toList());

                final String routeId = idGenerator.routeId(lineIdentifier, hash);
                final String routeName = commercialStops.get(0) + " - "
                        + commercialStops.get(commercialStops.size() - 1);
                final List<String> routePointRefs = allStopCodes.stream()
                        .map(idGenerator::routePointId)
                        .collect(Collectors.toList());

                routeMap.put(routeId, new NeTExRouteData.NeTExRoute(routeId, routeName, lineRef, routePointRefs));

                final List<NeTExRouteData.NeTExStopPointInPattern> stopPoints = new ArrayList<>();
                final String lastStopCode = commercialStops.get(commercialStops.size() - 1);

                for (int i = 0; i < commercialStops.size(); i++) {
                    final String code = commercialStops.get(i);
                    final boolean isFirst = (i == 0);
                    final boolean isLast = (i == commercialStops.size() - 1);
                    final String destRef = isFirst ? idGenerator.destinationDisplayId(lastStopCode) : null;

                    stopPoints.add(new NeTExRouteData.NeTExStopPointInPattern(
                            i + 1,
                            idGenerator.scheduledStopPointId(code),
                            !isLast,
                            !isFirst,
                            destRef));
                }

                patternMap.put(patternId, new NeTExRouteData.NeTExJourneyPattern(patternId, routeId, stopPoints));
            }
        }

        return new NeTExRouteData(
                new ArrayList<>(routeMap.values()),
                new ArrayList<>(patternMap.values()),
                scheduleToPatternId);
    }

    /**
     * Computes a deterministic hash from an ordered list of station short codes.
     */
    public String computeStopSequenceHash(final List<String> stationShortCodes) {
        return String.join("-", stationShortCodes);
    }

    private List<String> extractCommercialStopCodes(final Schedule schedule) {
        final List<String> codes = new ArrayList<>();
        for (final ScheduleRow row : schedule.scheduleRows) {
            if (isCommercialStop(row)) {
                codes.add(row.station.stationShortCode);
            }
        }
        return codes;
    }

    private boolean isCommercialStop(final ScheduleRow row) {
        if (row.departure != null && row.departure.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL) {
            return true;
        }
        return row.arrival != null && row.arrival.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL;
    }

    private String deriveLineIdentifier(final Schedule schedule) {
        if (schedule.commuterLineId != null && !schedule.commuterLineId.isEmpty()) {
            return schedule.commuterLineId;
        }
        return schedule.trainType.name;
    }
}
