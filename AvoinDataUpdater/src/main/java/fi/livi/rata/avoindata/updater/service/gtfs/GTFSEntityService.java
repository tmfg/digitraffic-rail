package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;

import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class GTFSEntityService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private GTFSStopsService gtfsStopsService;

    @Autowired
    private GTFSAgencyService gtfsAgencyService;

    @Autowired
    private GTFSTripService gtfsTripService;

    @Autowired
    private TodaysScheduleService todaysScheduleService;

    @Autowired
    private GTFSRouteService gtfsRouteService;

    @Autowired
    private GTFSShapeService gtfsShapeService;

    @Autowired
    private TimeTableRowService timeTableRowService;

    @Autowired
    private PlatformDataService platformDataService;

    @Autowired
    private TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    public GTFSDto createGTFSEntity(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) {
        final Map<Long, Map<DateRange, Schedule>> scheduleIntervalsByTrain = createScheduleIntervals(adhocSchedules, regularSchedules);
        final List<SimpleTimeTableRow> timeTableRows = timeTableRowService.getNextTenDays();
        final PlatformData platformData = platformDataService.getCurrentPlatformData();
        final Map<String, Stop> stopMap = gtfsStopsService.createStops(scheduleIntervalsByTrain, timeTableRows, platformData);

        final GTFSDto gtfsDto = new GTFSDto();

        gtfsDto.stops = Lists.newArrayList(stopMap.values());
        gtfsDto.agencies = gtfsAgencyService.createAgencies(scheduleIntervalsByTrain);
        gtfsDto.trips = gtfsTripService.createTrips(scheduleIntervalsByTrain, stopMap, timeTableRows, platformData);
        gtfsDto.routes = gtfsRouteService.createRoutesFromTrips(gtfsDto.trips, stopMap);
        gtfsDto.shapes = gtfsShapeService.createShapesFromTrips(gtfsDto.trips, stopMap);
        gtfsDto.translations = createTranslations(gtfsDto.stops);

        return gtfsDto;
    }

    private List<Translation> createTranslations(final List<Stop> stops) {
        final List<Translation> translations = new ArrayList<>();
        final Map<String, JsonNode> nodes = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes();

        for(final Stop stop : stops) {
            // skip stops with track, they are not needed for translations
            if(!stop.stopId.contains("_")) {
                final JsonNode trakediaNode = nodes.get(stop.stopCode);

                if (trakediaNode == null) {
                    log.info("Could not find station {} from trakedia", stop.stopCode);
                } else {
                    final JsonNode translationEn = trakediaNode.get(0).get("nimiEn");
                    final JsonNode translationSe = trakediaNode.get(0).get("nimiSe");

                    if (translationEn != null && !translationEn.isNull()) {
                        translations.add(new Translation(stop.name, "en", translationEn.asText()));
                    }

                    if (translationSe != null && !translationSe.isNull()) {
                        // language code is sv, even though trakedia uses se!
                        translations.add(new Translation(stop.name, "sv", translationSe.asText()));
                    }
                }
            }
        }

        return translations;
    }

    private Map<Long, Map<DateRange, Schedule>> createScheduleIntervals(final List<Schedule> adhocSchedules,
                                                                              final List<Schedule> regularSchedules) {
        final LocalDate start = DateProvider.dateInHelsinki().minusDays(7);
        final LocalDate end = start.plusYears(1).withMonth(12).withDayOfMonth(31);

        final Table<LocalDate, Long, Schedule> daysSchedulesByTrainNumber = HashBasedTable.create();

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            final List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, adhocSchedules, regularSchedules);
            for (final Schedule schedule : todaysSchedules) {
                if (!schedule.changeType.equals("P") && schedule.isRunOnDay(date)) {
                    daysSchedulesByTrainNumber.put(date, schedule.trainNumber, schedule);
                }
            }
        }

        final Map<Long, Map<DateRange, Schedule>> scheduleIntervals = new HashMap<>();

        for (final Long trainNumber : daysSchedulesByTrainNumber.columnKeySet()) {
            final Map<DateRange, Schedule> trainsScheduleIntervals = createIntervalForATrain(start, end, daysSchedulesByTrainNumber, trainNumber);
            final Map<DateRange, Schedule> endCorrectedTrainsScheduleIntervals = correctIntervalEnds(trainsScheduleIntervals);
            final Map<DateRange, Schedule> startCorrectedTrainsScheduleIntervals = correctIntervalStarts(endCorrectedTrainsScheduleIntervals);

            scheduleIntervals.put(trainsScheduleIntervals.values().iterator().next().trainNumber, startCorrectedTrainsScheduleIntervals);
        }
        return scheduleIntervals;
    }

    private Map<DateRange, Schedule> createIntervalForATrain(final LocalDate start, final LocalDate end,
                                                                   final Table<LocalDate, Long, Schedule> daysSchedulesByTrainNumber, final Long trainNumber) {
        final Map<DateRange, Schedule> trainsScheduleIntervals = new HashMap<>();

        Schedule previousSchedule = null;
        LocalDate intervalStart = start;
        for (LocalDate date = start; date.isBefore(end) || date.isEqual(end); date = date.plusDays(1)) {
            final Schedule schedule = daysSchedulesByTrainNumber.get(date, trainNumber);

            if (date.equals(end)) {
                final Schedule usedSchedule = schedule == null ? previousSchedule : schedule;
                final LocalDate intervalStartDate = usedSchedule.startDate.isAfter(intervalStart) ? usedSchedule.startDate : intervalStart;
                trainsScheduleIntervals.put(new DateRange(intervalStartDate, date), usedSchedule);
            } else if (schedule == null) {
                continue;
            } else if (previousSchedule != null && previousSchedule.id != schedule.id) {
                trainsScheduleIntervals.put(new DateRange(intervalStart, date.minusDays(1)), previousSchedule);
                intervalStart = date;
            }

            previousSchedule = schedule;
        }
        return trainsScheduleIntervals;
    }

    private Map<DateRange, Schedule> correctIntervalEnds(final Map<DateRange, Schedule> trainsScheduleIntervals) {
        final Map<DateRange, Schedule> correctedTrainsScheduleIntervals = new HashMap<>();
        for (final Map.Entry<DateRange, Schedule> entry : trainsScheduleIntervals.entrySet()) {
            final DateRange oldKey = entry.getKey();
            final LocalDate scheduleEndDate = entry.getValue().endDate;
            if (scheduleEndDate != null && oldKey.endDate.isAfter(scheduleEndDate)) {
                final DateRange newKey = new DateRange(oldKey.startDate, scheduleEndDate);
                correctedTrainsScheduleIntervals.put(newKey, entry.getValue());
            } else {
                correctedTrainsScheduleIntervals.put(entry.getKey(), entry.getValue());
            }
        }
        return correctedTrainsScheduleIntervals;
    }

    private Map<DateRange, Schedule> correctIntervalStarts(final Map<DateRange, Schedule> trainsScheduleIntervals) {
        final Map<DateRange, Schedule> correctedTrainsScheduleIntervals = new HashMap<>();
        for (final Map.Entry<DateRange, Schedule> entry : trainsScheduleIntervals.entrySet()) {
            final DateRange oldKey = entry.getKey();
            final LocalDate scheduleStartDate = entry.getValue().startDate;
            if (scheduleStartDate != null && oldKey.startDate.isBefore(scheduleStartDate)) {
                final DateRange newKey = new DateRange(scheduleStartDate, oldKey.endDate);
                correctedTrainsScheduleIntervals.put(newKey, entry.getValue());
            } else {
                correctedTrainsScheduleIntervals.put(entry.getKey(), entry.getValue());
            }
        }
        return correctedTrainsScheduleIntervals;
    }

}
