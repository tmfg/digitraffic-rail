package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Builds core NeTEx domain objects: Lines, Operators, Authority,
 * ServiceJourneys, Network.
 */
@Service
public class NeTExEntityService {

    private final NeTExIdGenerator idGenerator;
    private final NeTExTimeConverter timeConverter;

    public NeTExEntityService(final NeTExIdGenerator idGenerator, final NeTExTimeConverter timeConverter) {
        this.idGenerator = idGenerator;
        this.timeConverter = timeConverter;
    }

    /**
     * Derives the id-safe Line identifier for a schedule. Commuter trains use
     * commuterLineId;
     * long-distance trains use trainType + train number (e.g. "IC-59"), since "IC"
     * alone is
     * not a line but each numbered service is.
     */
    public String deriveLineId(final Schedule schedule) {
        if (schedule.commuterLineId != null && !schedule.commuterLineId.isEmpty()) {
            return schedule.commuterLineId;
        }
        return schedule.trainType.name + "-" + schedule.trainNumber;
    }

    /** Human-readable line code: commuter line id, or "IC 59" for long-distance. */
    private String deriveLinePublicCode(final Schedule schedule) {
        if (schedule.commuterLineId != null && !schedule.commuterLineId.isEmpty()) {
            return schedule.commuterLineId;
        }
        return schedule.trainType.name + " " + schedule.trainNumber;
    }

    /**
     * Creates the list of unique Lines from schedules. The Line name is the
     * corridor it serves ("Helsinki-Oulu"), since a code like "IC 140" tells a
     * passenger nothing on its own; the code stays in PublicCode.
     */
    public List<NeTExLine> createLines(final List<Schedule> schedules,
            final NeTExRouteData routeData,
            final Map<String, String> stationNamesByShortCode) {
        final Map<String, NeTExLine> lineMap = new LinkedHashMap<>();
        for (final Schedule schedule : schedules) {
            final String lineIdentifier = deriveLineId(schedule);
            final String lineId = idGenerator.lineId(lineIdentifier);
            if (!lineMap.containsKey(lineId)) {
                lineMap.put(lineId, new NeTExLine(lineId,
                        deriveLineName(lineId, routeData, stationNamesByShortCode, schedule),
                        deriveLinePublicCode(schedule),
                        lineIdentifier,
                        idGenerator.operatorId(schedule.operator.operatorShortCode),
                        "rail",
                        deriveTransportSubmode(schedule)));
            }
        }
        return new ArrayList<>(lineMap.values());
    }

    private static String deriveTransportSubmode(final Schedule schedule) {
        if ("Commuter".equals(schedule.trainCategory.name)) {
            return "local";
        }
        return switch (schedule.trainType.name) {
            case "HDM", "H" -> "regionalRail";
            case "PYO" -> "nightRail";
            case "MUS" -> "touristRailway";
            default -> "longDistance";
        };
    }

    /**
     * Names the Line after the longest route it serves, which is the variant that
     * best describes the corridor. Ring lines, whose origin and destination are the
     * same station, get their midpoint inserted so the name is not
     * "Helsinki-Helsinki".
     */
    private String deriveLineName(final String lineId,
            final NeTExRouteData routeData,
            final Map<String, String> stationNamesByShortCode,
            final Schedule schedule) {
        final NeTExRouteData.NeTExRoute longest = routeData.getRoutes().stream()
                .filter(route -> lineId.equals(route.lineRef()))
                .max(Comparator.comparingInt(route -> route.routePointRefs().size()))
                .orElse(null);
        if (longest == null) {
            return deriveLinePublicCode(schedule);
        }

        final String[] endpoints = longest.name().split(" - ", 2);
        if (endpoints.length < 2) {
            return deriveLinePublicCode(schedule);
        }
        final String origin = stationName(endpoints[0], stationNamesByShortCode);
        final String destination = stationName(endpoints[1], stationNamesByShortCode);

        if (!origin.equals(destination)) {
            return origin + "-" + destination;
        }
        final List<String> refs = longest.routePointRefs();
        if (refs.size() < 3) {
            return origin + "-" + destination;
        }
        final String viaCode = shortCodeOf(refs.get(refs.size() / 2));
        return origin + "-" + stationName(viaCode, stationNamesByShortCode) + "-" + destination;
    }

    private static String stationName(final String shortCode, final Map<String, String> stationNamesByShortCode) {
        return stationNamesByShortCode.getOrDefault(shortCode.trim(), shortCode.trim());
    }

    /** "FTR:RoutePoint:HKI" -> "HKI". */
    private static String shortCodeOf(final String routePointRef) {
        final int lastColon = routePointRef.lastIndexOf(':');
        return lastColon < 0 ? routePointRef : routePointRef.substring(lastColon + 1);
    }

    /**
     * Creates the list of unique Operators from schedules.
     */
    public List<NeTExOperator> createOperators(final List<Schedule> schedules) {
        final Map<String, NeTExOperator> operatorMap = new LinkedHashMap<>();
        for (final Schedule schedule : schedules) {
            final String shortCode = schedule.operator.operatorShortCode;
            final String operatorId = idGenerator.operatorId(shortCode);
            if (!operatorMap.containsKey(operatorId)) {
                operatorMap.put(operatorId, new NeTExOperator(
                        operatorId,
                        shortCode,
                        shortCode,
                        schedule.operator.operatorUICCode));
            }
        }
        return new ArrayList<>(operatorMap.values());
    }

    /**
     * Creates ServiceJourneys from schedules with calendar and route references.
     * Deduplicates by ID — if multiple schedules produce the same ServiceJourney ID
     * (e.g. multiple adhoc schedules for same train+date), the highest schedule.id
     * wins
     * (most recent version from RIPA).
     */
    public List<NeTExServiceJourney> createServiceJourneys(final List<Schedule> schedules,
            final NeTExRouteData routeData) {
        final Map<String, NeTExServiceJourney> journeyMap = new LinkedHashMap<>();
        final Map<String, Long> journeyScheduleIds = new LinkedHashMap<>();

        for (final Schedule schedule : schedules) {
            final String id = serviceJourneyIdFor(schedule);

            // Keep the schedule with the highest id (most recent version)
            if (journeyMap.containsKey(id) && journeyScheduleIds.get(id) >= schedule.id) {
                continue;
            }

            final String lineIdentifier = deriveLineId(schedule);
            final String name = schedule.trainType.name + " " + schedule.trainNumber;
            final String privateCode = String.valueOf(schedule.trainNumber);
            final String journeyPatternRef = routeData.getJourneyPatternIdForSchedule(schedule.id);

            final String operatorRef = idGenerator.operatorId(schedule.operator.operatorShortCode);
            final String lineRef = idGenerator.lineId(lineIdentifier);

            final List<NeTExPassingTime> passingTimes = buildPassingTimes(schedule, journeyPatternRef);

            journeyMap.put(id, new NeTExServiceJourney(id, name, privateCode,
                    journeyPatternRef, operatorRef, lineRef, passingTimes));
            journeyScheduleIds.put(id, schedule.id);
        }

        return new ArrayList<>(journeyMap.values());
    }

    /**
     * Computes the NeTEx ServiceJourney id for a schedule, matching the ids used
     * when building the timetable ServiceJourneys. ADHOC schedules are keyed by
     * departure date, REGULAR schedules by their schedule id.
     */
    public String serviceJourneyIdFor(final Schedule schedule) {
        if (schedule.timetableType == Train.TimetableType.ADHOC) {
            return idGenerator.serviceJourneyIdAdhoc(schedule.trainNumber, schedule.startDate);
        }
        return idGenerator.serviceJourneyId(schedule.trainNumber, schedule.id);
    }

    private List<NeTExPassingTime> buildPassingTimes(final Schedule schedule, final String journeyPatternRef) {
        final List<NeTExPassingTime> times = new ArrayList<>();
        final Duration firstDeparture = findFirstDeparture(schedule);
        int order = 1;

        for (final ScheduleRow row : schedule.scheduleRows) {
            if (!isCommercialStop(row)) {
                continue;
            }
            final String arrivalTime = row.arrival != null
                    ? timeConverter.toNeTExTime(row.arrival.timestamp, firstDeparture)
                    : null;
            final String departureTime = row.departure != null
                    ? timeConverter.toNeTExTime(row.departure.timestamp, firstDeparture)
                    : null;
            final String stopPointRef = idGenerator.stopPointInJourneyPatternId(journeyPatternRef, order);
            times.add(new NeTExPassingTime(order++, arrivalTime, departureTime,
                    row.station.stationShortCode, row.commercialTrack, stopPointRef));
        }

        return times;
    }

    /**
     * The baseline for the past-midnight rollover has to be the first stop we
     * actually publish, not the first one flagged COMMERCIAL: an origin whose
     * departure carries another stop type is still published, and measuring
     * from a later stop pushes everything before it past 24:00.
     */
    private Duration findFirstDeparture(final Schedule schedule) {
        for (final ScheduleRow row : schedule.scheduleRows) {
            if (isCommercialStop(row) && row.departure != null) {
                return row.departure.timestamp;
            }
        }
        return Duration.ZERO;
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

    public record NeTExLine(String id, String name, String publicCode, String privateCode,
            String operatorRef, String transportMode, String transportSubmode) {
    }

    public record NeTExOperator(String id, String name, String privateCode, int companyNumber) {
    }

    public record NeTExServiceJourney(String id, String name, String privateCode,
            String journeyPatternRef, String operatorRef,
            String lineRef,
            List<NeTExPassingTime> passingTimes) {
    }

    public record NeTExPassingTime(int order, String arrivalTime, String departureTime,
            String stationShortCode, String commercialTrack, String stopPointInJourneyPatternRef) {
    }
}
