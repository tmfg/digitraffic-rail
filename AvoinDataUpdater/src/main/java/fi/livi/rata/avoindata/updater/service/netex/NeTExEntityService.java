package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
     * Derives the Line identifier for a schedule.
     * Commuter trains use commuterLineId, long-distance trains use trainType name.
     */
    public String deriveLineId(final Schedule schedule) {
        if (schedule.commuterLineId != null && !schedule.commuterLineId.isEmpty()) {
            return schedule.commuterLineId;
        }
        return schedule.trainType.name;
    }

    /**
     * Creates the list of unique Lines from schedules.
     */
    public List<NeTExLine> createLines(final List<Schedule> schedules) {
        final Map<String, NeTExLine> lineMap = new LinkedHashMap<>();
        for (final Schedule schedule : schedules) {
            final String lineIdentifier = deriveLineId(schedule);
            final String lineId = idGenerator.lineId(lineIdentifier);
            if (!lineMap.containsKey(lineId)) {
                lineMap.put(lineId, new NeTExLine(lineId, lineIdentifier, "rail"));
            }
        }
        return new ArrayList<>(lineMap.values());
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
            final NeTExCalendarData calendarData,
            final NeTExRouteData routeData) {
        final Map<String, NeTExServiceJourney> journeyMap = new LinkedHashMap<>();
        final Map<String, Long> journeyScheduleIds = new LinkedHashMap<>();

        for (final Schedule schedule : schedules) {
            final String id;
            if (schedule.timetableType == Train.TimetableType.ADHOC) {
                id = idGenerator.serviceJourneyIdAdhoc(schedule.trainNumber, schedule.startDate);
            } else {
                id = idGenerator.serviceJourneyId(schedule.trainNumber, schedule.id);
            }

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
            final String dayTypeRef = calendarData.getDayTypeIdForSchedule(schedule.id);

            final List<NeTExPassingTime> passingTimes = buildPassingTimes(schedule);

            journeyMap.put(id, new NeTExServiceJourney(id, name, privateCode,
                    journeyPatternRef, operatorRef, lineRef, dayTypeRef, passingTimes));
            journeyScheduleIds.put(id, schedule.id);
        }

        return new ArrayList<>(journeyMap.values());
    }

    private List<NeTExPassingTime> buildPassingTimes(final Schedule schedule) {
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
            times.add(new NeTExPassingTime(order++, arrivalTime, departureTime));
        }

        return times;
    }

    private Duration findFirstDeparture(final Schedule schedule) {
        for (final ScheduleRow row : schedule.scheduleRows) {
            if (row.departure != null && row.departure.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL) {
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

    public record NeTExLine(String id, String publicCode, String transportMode) {
    }

    public record NeTExOperator(String id, String name, String privateCode, int companyNumber) {
    }

    public record NeTExServiceJourney(String id, String name, String privateCode,
            String journeyPatternRef, String operatorRef,
            String lineRef, String dayTypeRef,
            List<NeTExPassingTime> passingTimes) {
    }

    public record NeTExPassingTime(int order, String arrivalTime, String departureTime) {
    }
}
