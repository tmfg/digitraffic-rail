package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class GTFSEntityService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

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
    private DateProvider dp;

    public GTFSDto createGTFSEntity(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) {
        Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain = createScheduleIntervals(adhocSchedules, regularSchedules);

        GTFSDto gtfsDto = new GTFSDto();

        final Map<String, Stop> stopMap = gtfsStopsService.createStops(scheduleIntervalsByTrain);
        gtfsDto.stops = Lists.newArrayList(stopMap.values());
        gtfsDto.agencies = gtfsAgencyService.createAgencies(scheduleIntervalsByTrain);
        gtfsDto.trips = gtfsTripService.createTrips(scheduleIntervalsByTrain, stopMap);
        gtfsDto.routes = gtfsRouteService.createRoutesFromTrips(gtfsDto.trips, stopMap);

        return gtfsDto;
    }

    private Map<Long, Map<List<LocalDate>, Schedule>> createScheduleIntervals(final List<Schedule> adhocSchedules,
                                                                              final List<Schedule> regularSchedules) {
        LocalDate start = dp.dateInHelsinki().minusDays(7);
        LocalDate end = start.plusYears(1).withMonth(12).withDayOfMonth(31);

        Table<LocalDate, Long, Schedule> daysSchedulesByTrainNumber = HashBasedTable.create();

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            final List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, adhocSchedules, regularSchedules);
            for (final Schedule schedule : todaysSchedules) {
                if (!schedule.changeType.equals("P") && schedule.isRunOnDay(date)) {
                    daysSchedulesByTrainNumber.put(date, schedule.trainNumber, schedule);
                }
            }
        }

        Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervals = new HashMap<>();

        for (final Long trainNumber : daysSchedulesByTrainNumber.columnKeySet()) {
            Map<List<LocalDate>, Schedule> trainsScheduleIntervals = createIntervalForATrain(start, end, daysSchedulesByTrainNumber, trainNumber);
            Map<List<LocalDate>, Schedule> endCorrectedTrainsScheduleIntervals = correctIntervalEnds(trainsScheduleIntervals);
            Map<List<LocalDate>, Schedule> startCorrectedTrainsScheduleIntervals = correctIntervalStarts(endCorrectedTrainsScheduleIntervals);

            scheduleIntervals.put(trainsScheduleIntervals.values().iterator().next().trainNumber, startCorrectedTrainsScheduleIntervals);
        }
        return scheduleIntervals;
    }

    private Map<List<LocalDate>, Schedule> createIntervalForATrain(LocalDate start, LocalDate end, Table<LocalDate, Long, Schedule> daysSchedulesByTrainNumber, Long trainNumber) {
        Map<List<LocalDate>, Schedule> trainsScheduleIntervals = new HashMap<>();

        Schedule previousSchedule = null;
        LocalDate intervalStart = start;
        for (LocalDate date = start; date.isBefore(end) || date.isEqual(end); date = date.plusDays(1)) {
            Schedule schedule = daysSchedulesByTrainNumber.get(date, trainNumber);

            if (date.equals(end)) {
                Schedule usedSchedule = schedule == null ? previousSchedule : schedule;
                LocalDate intervalStartDate = usedSchedule.startDate.isAfter(intervalStart) ? usedSchedule.startDate : intervalStart;
                trainsScheduleIntervals.put(Arrays.asList(intervalStartDate, date), usedSchedule);
            } else if (schedule == null) {
                continue;
            } else if (previousSchedule != null && previousSchedule.id != schedule.id) {
                trainsScheduleIntervals.put(Arrays.asList(intervalStart, date.minusDays(1)), previousSchedule);
                intervalStart = date;
            }

            previousSchedule = schedule;
        }
        return trainsScheduleIntervals;
    }

    private Map<List<LocalDate>, Schedule> correctIntervalEnds(Map<List<LocalDate>, Schedule> trainsScheduleIntervals) {
        Map<List<LocalDate>, Schedule> correctedTrainsScheduleIntervals = new HashMap<>();
        for (Map.Entry<List<LocalDate>, Schedule> entry : trainsScheduleIntervals.entrySet()) {
            List<LocalDate> oldKey = entry.getKey();
            LocalDate scheduleEndDate = entry.getValue().endDate;
            if (scheduleEndDate != null && oldKey.get(1).isAfter(scheduleEndDate)) {
                List<LocalDate> newKey = Lists.newArrayList(oldKey.get(0), scheduleEndDate);
                correctedTrainsScheduleIntervals.put(newKey, entry.getValue());
            } else {
                correctedTrainsScheduleIntervals.put(entry.getKey(), entry.getValue());
            }
        }
        return correctedTrainsScheduleIntervals;
    }

    private Map<List<LocalDate>, Schedule> correctIntervalStarts(Map<List<LocalDate>, Schedule> trainsScheduleIntervals) {
        Map<List<LocalDate>, Schedule> correctedTrainsScheduleIntervals = new HashMap<>();
        for (Map.Entry<List<LocalDate>, Schedule> entry : trainsScheduleIntervals.entrySet()) {
            List<LocalDate> oldKey = entry.getKey();
            LocalDate scheduleStartDate = entry.getValue().startDate;
            if (scheduleStartDate != null && oldKey.get(0).isBefore(scheduleStartDate)) {
                List<LocalDate> newKey = Lists.newArrayList(scheduleStartDate, oldKey.get(1));
                correctedTrainsScheduleIntervals.put(newKey, entry.getValue());
            } else {
                correctedTrainsScheduleIntervals.put(entry.getKey(), entry.getValue());
            }
        }
        return correctedTrainsScheduleIntervals;
    }
}
