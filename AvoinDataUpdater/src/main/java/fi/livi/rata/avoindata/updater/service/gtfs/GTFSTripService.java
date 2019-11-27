package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import fi.livi.rata.avoindata.common.utils.DateUtils;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Calendar;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.CalendarDate;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

@Service
public class GTFSTripService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private Map<String, CalendarDate> encounteredCalendarDates = new HashMap<>();

    public List<Trip> createTrips(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain,
                                  final Map<String, Stop> stopMap) {
        List<Trip> trips = new ArrayList<>();

        encounteredCalendarDates.clear();

        for (final Long trainNumber : scheduleIntervalsByTrain.keySet()) {
            final Map<List<LocalDate>, Schedule> trainsSchedules = scheduleIntervalsByTrain.get(trainNumber);
            for (final List<LocalDate> localDates : trainsSchedules.keySet()) {
                final Schedule schedule = trainsSchedules.get(localDates);

                final Trip trip = createTrip(schedule, localDates.get(0), localDates.get(1), "");
                trips.add(trip);

                List<Trip> partialCancellationTrips = createPartialCancellationTrips(localDates, schedule, trip);
                if (!partialCancellationTrips.isEmpty()) {
                    log.trace("Created {} partial cancellation trips: {}", partialCancellationTrips.size(), partialCancellationTrips);

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

            log.trace("Creating cancellation trip from {}", scheduleCancellation);
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
                log.trace("Collision between two cancellations: {} and {}", existingCancellation, cancellation);
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
        String key = String.format("%s_%s", serviceId, date.toString());
        CalendarDate calendarDate = encounteredCalendarDates.get(key);

        if (calendarDate == null) {
            calendarDate = new CalendarDate();
            calendarDate.serviceId = serviceId;
            calendarDate.date = date;
            calendarDate.exceptionType = cancelled ? 2 : 1;
            encounteredCalendarDates.put(key, calendarDate);

            return calendarDate;
        }

        return calendarDate;
    }

    private Boolean runOnDayToString(Boolean runOnDay, DayOfWeek dayOfWeek, LocalDate departureDate) {
        if (runOnDay != null) {
            return runOnDay;
        } else {
            return departureDate.getDayOfWeek().equals(dayOfWeek);
        }
    }
}
