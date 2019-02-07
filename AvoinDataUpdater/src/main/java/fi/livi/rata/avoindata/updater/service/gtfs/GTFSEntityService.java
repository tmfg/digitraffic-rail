package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.utils.DateUtils;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Calendar;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.*;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
public class GTFSEntityService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TodaysScheduleService todaysScheduleService;

    @Autowired
    private GTFSTrainTypeService gtfsTrainTypeService;

    @Autowired
    private DateProvider dp;


    public GTFSDto createGTFSEntity(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) {
        Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain = createScheduleIntervals(adhocSchedules, regularSchedules);

        GTFSDto gtfsDto = new GTFSDto();

        final Map<String, Stop> stopMap = createStops(scheduleIntervalsByTrain);
        gtfsDto.stops = Lists.newArrayList(stopMap.values());
        gtfsDto.agencies = createAgencies(scheduleIntervalsByTrain);
        gtfsDto.trips = createTrips(scheduleIntervalsByTrain, stopMap);
        gtfsDto.routes = createRoutesFromTrips(gtfsDto.trips, stopMap);

        return gtfsDto;
    }

    private List<Route> createRoutesFromTrips(final List<Trip> trips, final Map<String, Stop> stopMap) {
        Map<String, Route> routeMap = new HashMap<>();

        for (final Trip trip : trips) {
            final Route route = new Route();
            final Schedule schedule = trip.source;

            final StopTime firstStop = trip.stopTimes.get(0);
            final StopTime lastStop = trip.stopTimes.get(trip.stopTimes.size() - 1);

            final String routeId = getRouteId(trip);
            route.routeId = routeId;
            route.agencyId = schedule.operator.operatorUICCode;
            route.longName = String.format("%s - %s", stopMap.get(firstStop.stopId).name, stopMap.get(lastStop.stopId).name);

            if (Strings.isNullOrEmpty(schedule.commuterLineId)) {
                route.shortName = "";
            } else {
                route.shortName = schedule.commuterLineId;
            }

            route.type = gtfsTrainTypeService.getGtfsTrainType(schedule);

            trip.routeId = routeId;

            routeMap.putIfAbsent(route.routeId, route);
        }

        return Lists.newArrayList(routeMap.values());
    }


    private String getRouteId(final Trip trip) {
        final StopTime firstStop = trip.stopTimes.get(0);
        final StopTime lastStop = trip.stopTimes.get(trip.stopTimes.size() - 1);
        final Long id = new Long(String.format("%s_%s_%s_%s_%s", firstStop.stopId, lastStop.stopId, trip.source.commuterLineId,
                gtfsTrainTypeService.getGtfsTrainType(trip.source), trip.source.operator.operatorUICCode).hashCode()) + Integer.MAX_VALUE;
        return id.toString();
    }

    private Map<String, Stop> createStops(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain) {
        List<Stop> stops = new ArrayList<>();


        Map<Integer, StationEmbeddable> stationMap = new HashMap<>();
        for (final Long trainNumber : scheduleIntervalsByTrain.keySet()) {
            final Map<List<LocalDate>, Schedule> trainsSchedules = scheduleIntervalsByTrain.get(trainNumber);
            for (final List<LocalDate> localDates : trainsSchedules.keySet()) {
                final Schedule schedule = trainsSchedules.get(localDates);
                for (final ScheduleRow scheduleRow : schedule.scheduleRows) {
                    stationMap.putIfAbsent(scheduleRow.station.stationUICCode, scheduleRow.station);
                }
            }
        }

        for (final StationEmbeddable stationEmbeddable : stationMap.values()) {
            final Station station = stationRepository.findByShortCode(stationEmbeddable.stationShortCode);

            Stop stop = new Stop(station);
            stop.stopId = stationEmbeddable.stationShortCode;
            stop.stopCode = stationEmbeddable.stationShortCode;

            if (station != null) {
                stop.name = station.name;
                stop.latitude = station.latitude.doubleValue();
                stop.longitude = station.longitude.doubleValue();
            }

            stops.add(stop);
        }

        return Maps.uniqueIndex(stops, s -> s.stopId);
    }

    private List<Trip> createTrips(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain,
                                   final Map<String, Stop> stopMap) {
        List<Trip> trips = new ArrayList<>();

        for (final Long trainNumber : scheduleIntervalsByTrain.keySet()) {
            final Map<List<LocalDate>, Schedule> trainsSchedules = scheduleIntervalsByTrain.get(trainNumber);
            for (final List<LocalDate> localDates : trainsSchedules.keySet()) {
                final Schedule schedule = trainsSchedules.get(localDates);

                final Trip trip = createTrip(schedule, localDates.get(0), localDates.get(1), "");
                trips.add(trip);

                List<Trip> partialCancellationTrips = createPartialCancellationTrips(localDates, schedule, trip);
                if (!partialCancellationTrips.isEmpty()) {
                    log.info("Created {} partial cancellation trips: {}", partialCancellationTrips.size(), partialCancellationTrips);

                    trips.addAll(partialCancellationTrips);
                }
            }
        }

        Set<Trip> toBeRemoved = new HashSet<>();
        for (final Trip trip : trips) {
            if (trip.stopTimes.isEmpty()) {
                toBeRemoved.add(trip);
            } else {
                trip.headsign = stopMap.get(Iterables.getLast(trip.stopTimes).stopId).name;
            }
        }

        trips.removeAll(toBeRemoved);

        return trips;
    }

    private List<Trip> createPartialCancellationTrips(final List<LocalDate> localDates, final Schedule schedule, final Trip trip) {
        List<Trip> partialCancellationTrips = new ArrayList<>();

        Table<LocalDate, LocalDate, ScheduleCancellation> cancellations = getFilteredCancellations(schedule);

        for (final ScheduleCancellation scheduleCancellation : cancellations.values()) {
            if (!DateUtils.isInclusivelyBetween(scheduleCancellation.startDate, localDates.get(0), localDates.get(1)) && !DateUtils
                    .isInclusivelyBetween(scheduleCancellation.endDate, localDates.get(0), localDates.get(1))) {
                continue;
            }

            LocalDate cancellationStartDate = DateUtils.isInclusivelyBetween(scheduleCancellation.startDate, localDates.get(0),
                    localDates.get(1)) ? scheduleCancellation.startDate : localDates.get(0);
            LocalDate cancellationEndDate = DateUtils.isInclusivelyBetween(scheduleCancellation.endDate, localDates.get(0),
                    localDates.get(1)) ? scheduleCancellation.endDate : localDates.get(1);

            for (LocalDate date = cancellationStartDate; date.isBefore(cancellationEndDate) || date.isEqual(
                    cancellationEndDate); date = date.plusDays(1)) {
                trip.calendar.calendarDates.add(createCalendarDate(trip.serviceId, date, true));
            }

            log.info("Creating cancellation trip from {}", scheduleCancellation);
            final Trip partialCancellationTrip = createTrip(schedule, cancellationStartDate, cancellationEndDate, "_replacement");
            partialCancellationTrip.calendar.calendarDates.clear();

            final Map<Long, ScheduleRowPart> cancelledScheduleRowsMap = Maps.uniqueIndex(
                    Collections2.filter(scheduleCancellation.cancelledRows, s -> s != null), s -> s.id);

            List<StopTime> removedStopTimes = new ArrayList<>();
            for (final StopTime stopTime : partialCancellationTrip.stopTimes) {
                if (isStoptimeCancelled(stopTime, cancelledScheduleRowsMap)) {
                    removedStopTimes.add(stopTime);
                }
            }
            partialCancellationTrip.stopTimes.removeAll(removedStopTimes);

            partialCancellationTrips.add(partialCancellationTrip);
        }
        return partialCancellationTrips;
    }

    private Table<LocalDate, LocalDate, ScheduleCancellation> getFilteredCancellations(final Schedule schedule) {
        final Collection<ScheduleCancellation> partialCancellations = Collections2.filter(schedule.scheduleCancellations,
                sc -> sc.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.PARTIALLY);
        final Collection<ScheduleCancellation> differentRouteCancellations = Collections2.filter(schedule.scheduleCancellations,
                sc -> sc.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE);

        final Iterable<ScheduleCancellation> allCancellations = Iterables.concat(partialCancellations, differentRouteCancellations);
        Table<LocalDate, LocalDate, ScheduleCancellation> cancellations = HashBasedTable.create();
        for (final ScheduleCancellation cancellation : allCancellations) {
            final ScheduleCancellation existingCancellation = cancellations.get(cancellation.startDate, cancellation.endDate);
            if (existingCancellation == null) {
                cancellations.put(cancellation.startDate, cancellation.endDate, cancellation);
            } else {
                log.info("Collision between two cancellations: {} and {}", existingCancellation, cancellation);
                if (existingCancellation.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE &&
                        cancellation.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE) {
                    existingCancellation.cancelledRows.addAll(cancellation.cancelledRows);
                } else if (existingCancellation.id < cancellation.id) {
                    cancellations.put(cancellation.startDate, cancellation.endDate, cancellation);
                }
            }
        }
        return cancellations;
    }

    private boolean isStoptimeCancelled(final StopTime stopTime, final Map<Long, ScheduleRowPart> cancelledRows) {
        boolean arrivalExists = stopTime.source.arrival != null;
        boolean departureExists = stopTime.source.departure != null;

        boolean arrivalCancelled = arrivalExists && cancelledRows.containsKey(stopTime.source.arrival.id);
        boolean departureCancelled = departureExists && cancelledRows.containsKey(stopTime.source.departure.id);

        return departureCancelled && !arrivalExists || arrivalCancelled && !departureExists || arrivalCancelled && departureCancelled;
    }

    private Trip createTrip(final Schedule schedule, final LocalDate startDate, final LocalDate endDate, String scheduleSuffix) {
        final String tripId = String.format("%s_%s_%s%s", schedule.trainNumber, startDate, endDate, scheduleSuffix);
        final String serviceId = tripId;

        Trip trip = new Trip(schedule);
        trip.serviceId = serviceId;
        trip.tripId = tripId;

        if (Strings.isNullOrEmpty(schedule.commuterLineId)) {
            trip.shortName = String.format("%s %s", schedule.trainType.name, schedule.trainNumber);
        } else {
            trip.shortName = String.format("%s (%s %s)", schedule.commuterLineId, schedule.trainType.name, schedule.trainNumber);
        }

        trip.calendar = createCalendar(schedule, serviceId, startDate, endDate);
        trip.calendar.calendarDates = createCalendarDatesFromExceptions(schedule, serviceId);
        trip.stopTimes = createStopTimes(schedule, tripId);

        return trip;
    }

    private Calendar createCalendar(final Schedule schedule, final String serviceId, final LocalDate startDate, final LocalDate endDate) {
        final Calendar calendar = new Calendar();
        calendar.serviceId = serviceId;
        calendar.monday = runOnDayToString(schedule.runOnMonday, DayOfWeek.MONDAY, schedule.startDate);
        calendar.tuesday = runOnDayToString(schedule.runOnTuesday, DayOfWeek.TUESDAY, schedule.startDate);
        calendar.wednesday = runOnDayToString(schedule.runOnWednesday, DayOfWeek.WEDNESDAY, schedule.startDate);
        calendar.thursday = runOnDayToString(schedule.runOnThursday, DayOfWeek.THURSDAY, schedule.startDate);
        calendar.friday = runOnDayToString(schedule.runOnFriday, DayOfWeek.FRIDAY, schedule.startDate);
        calendar.saturday = runOnDayToString(schedule.runOnSaturday, DayOfWeek.SATURDAY, schedule.startDate);
        calendar.sunday = runOnDayToString(schedule.runOnSunday, DayOfWeek.SUNDAY, schedule.startDate);
        calendar.startDate = startDate;
        calendar.endDate = MoreObjects.firstNonNull(endDate, schedule.startDate);
        return calendar;
    }

    private List<StopTime> createStopTimes(final Schedule schedule, final String tripId) {
        List<StopTime> stopTimes = new ArrayList<>();
        for (int i = 0; i < schedule.scheduleRows.size(); i++) {
            ScheduleRow scheduleRow = schedule.scheduleRows.get(i);
            StopTime stopTime = new StopTime(scheduleRow);
            stopTime.tripId = tripId;

            if (scheduleRow.departure != null) {
                stopTime.departureTime = scheduleRow.departure.timestamp;
            } else {
                stopTime.departureTime = scheduleRow.arrival.timestamp;
            }
            if (scheduleRow.arrival != null) {
                stopTime.arrivalTime = scheduleRow.arrival.timestamp;
            } else {
                stopTime.arrivalTime = scheduleRow.departure.timestamp;
            }

            stopTime.stopId = scheduleRow.station.stationShortCode;
            stopTime.stopSequence = i;

            final boolean isLongStop = scheduleRow.departure == null || scheduleRow.arrival == null || !stopTime.departureTime.equals(
                    stopTime.arrivalTime);
            stopTime.pickupType = isLongStop ? 0 : 1;
            stopTime.dropoffType = isLongStop ? 0 : 1;

            stopTimes.add(stopTime);
        }
        return stopTimes;
    }

    private List<CalendarDate> createCalendarDatesFromExceptions(final Schedule schedule, final String serviceId) {
        List<CalendarDate> calendarDates = new ArrayList<>();
        for (final ScheduleException scheduleException : schedule.scheduleExceptions) {
            calendarDates.add(createCalendarDate(serviceId, scheduleException.date, !scheduleException.isRun));
        }

        final Collection<ScheduleCancellation> wholeDayCancellations = Collections2.filter(schedule.scheduleCancellations,
                c -> c.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY);
        for (final ScheduleCancellation scheduleCancellation : wholeDayCancellations) {
            for (LocalDate date = scheduleCancellation.startDate; date.isBefore(scheduleCancellation.endDate) || date.isEqual(
                    scheduleCancellation.endDate); date = date.plusDays(1)) {
                calendarDates.add(createCalendarDate(serviceId, date, true));
            }
        }
        return calendarDates;
    }

    private CalendarDate createCalendarDate(final String serviceId, final LocalDate date, final boolean cancelled) {
        CalendarDate calendarDate = new CalendarDate();
        calendarDate.serviceId = serviceId;
        calendarDate.date = date;
        calendarDate.exceptionType = cancelled ? 2 : 1;
        return calendarDate;
    }

    private Boolean runOnDayToString(Boolean runOnDay, DayOfWeek dayOfWeek, LocalDate departureDate) {
        if (runOnDay != null) {
            return runOnDay;
        } else {
            return departureDate.getDayOfWeek().equals(dayOfWeek);
        }
    }

    private List<Agency> createAgencies(Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain) {
        List<Agency> agencies = new ArrayList<>();

        Map<Integer, Operator> operators = new HashMap<>();
        for (final Map<List<LocalDate>, Schedule> trainsSchedules : scheduleIntervalsByTrain.values()) {
            for (final Schedule schedule : trainsSchedules.values()) {
                operators.putIfAbsent(schedule.operator.operatorUICCode, schedule.operator);
            }
        }


        for (final Operator operator : operators.values()) {
            final Agency agency = new Agency();
            agency.name = operator.operatorShortCode;
            agency.id = operator.operatorUICCode;
            agency.url = "http://";
            agency.timezone = "Europe/Helsinki";
            agencies.add(agency);
        }

        return agencies;
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
            Map<List<LocalDate>, Schedule> trainsScheduleIntervals = new HashMap<>();

            Schedule previousSchedule = null;
            LocalDate intervalStart = start;
            for (LocalDate date = start; date.isBefore(end) || date.isEqual(end); date = date.plusDays(1)) {
                Schedule schedule = daysSchedulesByTrainNumber.get(date, trainNumber);

                //  log.info("{}: {}", date, schedule != null ? schedule.id : "null");

                if (date.equals(end)) {
                    trainsScheduleIntervals.put(Arrays.asList(intervalStart, date), schedule == null ? previousSchedule : schedule);
                } else if (schedule == null) {
                    continue;
                } else if (previousSchedule != null && previousSchedule.id != schedule.id) {
                    trainsScheduleIntervals.put(Arrays.asList(intervalStart, date.minusDays(1)), previousSchedule);
                    intervalStart = date;
                }

                previousSchedule = schedule;
            }

            scheduleIntervals.put(trainsScheduleIntervals.values().iterator().next().trainNumber, trainsScheduleIntervals);
        }
        return scheduleIntervals;
    }
}
