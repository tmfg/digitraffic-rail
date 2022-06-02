package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.Table;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateUtils;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Calendar;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.CalendarDate;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;
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

    public static final String TRIP_REPLACEMENT = "_replacement";

    @Autowired
    private CancellationFlattener cancellationFlattener;

    @Autowired
    private GTFSTripRepository gtfsTripRepository;

    private Map<String, CalendarDate> encounteredCalendarDates = new HashMap<>();

    public List<Trip> createTrips(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain,
                                  final Map<String, Stop> stopMap, final List<SimpleTimeTableRow> timeTableRows,
                                  final Map<String, Map <String, InfraApiPlatform>> platformsByStationAndTrack) {
        List<Trip> trips = new ArrayList<>();

        Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber = timeTableRows
                .stream()
                .collect(Collectors.groupingBy(SimpleTimeTableRow::getTrainNumber));

        for (final Long trainNumber : scheduleIntervalsByTrain.keySet()) {
            final Map<List<LocalDate>, Schedule> trainsSchedules = scheduleIntervalsByTrain.get(trainNumber);
            for (final List<LocalDate> localDates : trainsSchedules.keySet()) {
                final Schedule schedule = trainsSchedules.get(localDates);

                final Trip trip = createTrip(schedule, localDates.get(0), localDates.get(1), "", timeTableRowsByTrainNumber, platformsByStationAndTrack);

                List<Trip> partialCancellationTrips = createPartialCancellationTrips(localDates, schedule, trip, timeTableRowsByTrainNumber, platformsByStationAndTrack);
                if (!partialCancellationTrips.isEmpty()) {
                    log.trace("Created {} partial cancellation trips: {}", partialCancellationTrips.size(), partialCancellationTrips);

                    trips.addAll(partialCancellationTrips);
                }

                if (!isTripFullyCancelled(trip, partialCancellationTrips)) {
                    trips.add(trip);
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

        encounteredCalendarDates.clear();

        return trips;
    }

    private boolean isTripFullyCancelled(Trip trip, List<Trip> partialCancellationTrips) {
        boolean isFullyCancelled = false;
        for (Trip partialCancellationTrip : partialCancellationTrips) {
            if ((partialCancellationTrip.calendar.startDate.equals(trip.calendar.startDate) && partialCancellationTrip.calendar.endDate.equals(trip.calendar.endDate))) {
                isFullyCancelled = true;
            }
        }
        return isFullyCancelled;
    }

    private List<Trip> createPartialCancellationTrips(final List<LocalDate> localDates, final Schedule schedule, final Trip trip,
                                                      final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber,
                                                      final Map<String, Map <String, InfraApiPlatform>> platFormsByStationAndTrack) {
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
            final Trip partialCancellationTrip = createTrip(schedule, cancellationStartDate, cancellationEndDate, TRIP_REPLACEMENT, timeTableRowsByTrainNumber, platFormsByStationAndTrack);
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
        Collection<ScheduleCancellation> differentRouteCancellations = Collections2.filter(schedule.scheduleCancellations,
                sc -> sc.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE);

        if (differentRouteCancellations.size() > 1) {
            differentRouteCancellations = cancellationFlattener.flatten(differentRouteCancellations);
        }

        handleConnectedPartialCancellations(partialCancellations);
        return handleEqualDoubleCancellations(partialCancellations, differentRouteCancellations);
    }

    private Table<LocalDate, LocalDate, ScheduleCancellation> handleEqualDoubleCancellations(Collection<ScheduleCancellation> partialCancellations, Collection<ScheduleCancellation> differentRouteCancellations) {
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

    private void handleConnectedPartialCancellations(Collection<ScheduleCancellation> partialCancellations) {
        for (ScheduleCancellation left : partialCancellations) {
            Range<LocalDate> leftRange = Range.closed(left.startDate, left.endDate);
            for (ScheduleCancellation right : partialCancellations) {
                if (left != right) {
                    Range<LocalDate> rightRange = Range.closed(right.startDate, right.endDate);
                    if (leftRange.isConnected(rightRange) && !leftRange.equals(rightRange)) {
                        Range<LocalDate> newRange = leftRange.span(rightRange);

                        log.info("Collision between two partial cancellations {}->{} and {}->{}", left.startDate, left.endDate, right.startDate, right.endDate);

                        left.startDate = newRange.lowerEndpoint();
                        left.endDate = newRange.upperEndpoint();

                        right.startDate = newRange.lowerEndpoint();
                        right.endDate = newRange.upperEndpoint();
                    }
                }
            }
        }
    }

    private boolean isStoptimeCancelled(final StopTime stopTime, final Map<Long, ScheduleRowPart> cancelledRows) {
        boolean arrivalExists = stopTime.source.arrival != null;
        boolean departureExists = stopTime.source.departure != null;

        boolean arrivalCancelled = arrivalExists && cancelledRows.containsKey(stopTime.source.arrival.id);
        boolean departureCancelled = departureExists && cancelledRows.containsKey(stopTime.source.departure.id);

        return departureCancelled && !arrivalExists || arrivalCancelled && !departureExists || arrivalCancelled && departureCancelled;
    }

    private Trip createTrip(final Schedule schedule, final LocalDate startDate, final LocalDate endDate, String scheduleSuffix,
                            final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber,
                            final Map<String, Map <String, InfraApiPlatform>> platFormsByStationAndTrack) {
        final String tripId = String.format("%s_%s_%s%s", schedule.trainNumber, startDate.format(DateTimeFormatter.BASIC_ISO_DATE), endDate.format(DateTimeFormatter.BASIC_ISO_DATE), scheduleSuffix);
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
        trip.stopTimes = createStopTimes(schedule, tripId, timeTableRowsByTrainNumber, platFormsByStationAndTrack);

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

    private Optional<String> findTrack(final ScheduleRow scheduleRow, final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber) {
        final Long trainNumber = scheduleRow.schedule.trainNumber;

        return timeTableRowsByTrainNumber.getOrDefault(trainNumber, Collections.emptyList())
                .stream()
                .filter(simpleTimeTableRow ->
                {
                    if (scheduleRow.arrival != null) {
                        return simpleTimeTableRow.type.equals(TimeTableRow.TimeTableRowType.ARRIVAL)
                                && simpleTimeTableRow.id.attapId.equals(scheduleRow.arrival.id)
                                && scheduleRow.schedule.isRunOnDay(simpleTimeTableRow.scheduledTime.toLocalDate());

                    } else if (scheduleRow.departure != null) {
                        return simpleTimeTableRow.type.equals(TimeTableRow.TimeTableRowType.DEPARTURE)
                                && simpleTimeTableRow.id.attapId.equals(scheduleRow.departure.id)
                                && scheduleRow.schedule.isRunOnDay(simpleTimeTableRow.scheduledTime.toLocalDate());
                    }

                    return false;
                })
                .map(matchingRow -> matchingRow.commercialTrack)
                .findAny();
    }

    private List<StopTime> createStopTimes(final Schedule schedule, final String tripId,
                                           final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber,
                                           final Map<String, Map <String, InfraApiPlatform>> platFormsByStationAndTrack) {
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

            Optional<String> trackNumber = findTrack(scheduleRow, timeTableRowsByTrainNumber);
            if (trackNumber.isPresent()
                    && platFormsByStationAndTrack.getOrDefault(scheduleRow.station.stationShortCode, Collections.emptyMap()).containsKey(trackNumber.get())) {
                stopTime.track = trackNumber.get();
            }

            stopTime.stopId = scheduleRow.station.stationShortCode;
            stopTime.stopSequence = i;

            final boolean isLongStop =
                    scheduleRow.departure == null || scheduleRow.arrival == null ||
                            (!stopTime.departureTime.equals(stopTime.arrivalTime) && (scheduleRow.arrival.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL || scheduleRow.departure.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL));
            stopTime.pickupType = isLongStop ? 0 : 1;
            stopTime.dropoffType = isLongStop ? 0 : 1;

            stopTimes.add(stopTime);
        }

        stopTimes.get(0).dropoffType = 1;
        Iterables.getLast(stopTimes).pickupType = 1;

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

    @Transactional
    public void updateGtfsTrips(final GTFSDto gtfs) {
        final List<GTFSTrip> gtfsTrips = gtfs.trips.stream()
                .map(trip -> new GTFSTrip(trip.source.trainNumber, trip.calendar.startDate, trip.calendar.endDate, trip.tripId, trip.routeId, trip.source.version))
                .collect(Collectors.toList());

        gtfsTripRepository.deleteAll();
        gtfsTripRepository.saveAll(gtfsTrips);
    }
}
