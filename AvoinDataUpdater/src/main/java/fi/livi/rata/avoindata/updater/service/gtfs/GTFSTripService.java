package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.utils.DateUtils;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Calendar;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.CalendarDate;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.PlatformData;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import jakarta.transaction.Transactional;

@Service
public class GTFSTripService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static final String TRIP_REPLACEMENT = "_replacement";

    @Autowired
    private CancellationFlattener cancellationFlattener;

    @Autowired
    private GTFSTripRepository gtfsTripRepository;

    private final Map<String, CalendarDate> encounteredCalendarDates = new HashMap<>();

    public List<Trip> createTrips(final Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain,
                                  final Map<String, Stop> stopMap, final List<SimpleTimeTableRow> timeTableRows,
                                  final PlatformData platformData) {
        final List<Trip> trips = new ArrayList<>();

        final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber = timeTableRows
                .stream()
                .collect(Collectors.groupingBy(SimpleTimeTableRow::getTrainNumber));

        for (final Long trainNumber : scheduleIntervalsByTrain.keySet()) {
            final Map<List<LocalDate>, Schedule> trainsSchedules = scheduleIntervalsByTrain.get(trainNumber);
            for (final List<LocalDate> localDates : trainsSchedules.keySet()) {
                final Schedule schedule = trainsSchedules.get(localDates);

                final Trip trip = createTrip(schedule, localDates.get(0), localDates.get(1), "", timeTableRowsByTrainNumber, platformData);

                final List<Trip> partialCancellationTrips = createPartialCancellationTrips(localDates, schedule, trip, timeTableRowsByTrainNumber, platformData);
                if (!partialCancellationTrips.isEmpty()) {
                    log.trace("Created {} partial cancellation trips: {}", partialCancellationTrips.size(), partialCancellationTrips);

                    trips.addAll(partialCancellationTrips);
                }

                if (!isTripFullyCancelled(trip, partialCancellationTrips)) {
                    trips.add(trip);
                }
            }
        }

        final LocalDate now = DateProvider.dateInHelsinki();
        final Set<Trip> toBeRemoved = new HashSet<>();
        for (final Trip trip : trips) {
            if (trip.stopTimes.isEmpty() || trip.calendar.endDate.isBefore(now)) {
                toBeRemoved.add(trip);
            } else {
                trip.headsign = stopMap.get(Iterables.getLast(trip.stopTimes).stopId).name;
            }
        }

        trips.removeAll(toBeRemoved);

        encounteredCalendarDates.clear();

        for (final Trip trip : trips) {
            trip.headsign = trip.headsign.replace(" asema", "");
        }

        for (final Trip trip : trips) {
            if (trip.stopTimes != null) {
                if (trip.stopTimes.get(0).stopId.equals("HKI") && Iterables.getLast(trip.stopTimes).stopId.equals("HKI") && trip.stopTimes.stream().map(s -> s.stopId).anyMatch(s -> s.equals("LEN"))) {
                    trip.headsign = "Helsinki -> Lentoasema -> Helsinki";
                }
            } else {
                log.error("Encountered trip without stoptimes: {}", trip);
            }
        }

        return trips;
    }

    private boolean isTripFullyCancelled(final Trip trip, final List<Trip> partialCancellationTrips) {
        for (final Trip partialCancellationTrip : partialCancellationTrips) {
            if ((partialCancellationTrip.calendar.startDate.equals(trip.calendar.startDate) && partialCancellationTrip.calendar.endDate.equals(trip.calendar.endDate))) {
                return true;
            }
        }
        return false;
    }

    private List<Trip> createPartialCancellationTrips(final List<LocalDate> localDates, final Schedule schedule, final Trip trip,
                                                      final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber,
                                                      final PlatformData platformData) {
        final List<Trip> partialCancellationTrips = new ArrayList<>();
        final Table<LocalDate, LocalDate, ScheduleCancellation> cancellations = getFilteredCancellations(schedule);

        for (final ScheduleCancellation scheduleCancellation : cancellations.values()) {
            if (!DateUtils.isInclusivelyBetween(scheduleCancellation.startDate, localDates.get(0), localDates.get(1)) && !DateUtils
                    .isInclusivelyBetween(scheduleCancellation.endDate, localDates.get(0), localDates.get(1))) {
                continue;
            }

            final LocalDate cancellationStartDate = DateUtils.isInclusivelyBetween(scheduleCancellation.startDate, localDates.get(0),
                    localDates.get(1)) ? scheduleCancellation.startDate : localDates.get(0);
            final LocalDate cancellationEndDate = DateUtils.isInclusivelyBetween(scheduleCancellation.endDate, localDates.get(0),
                    localDates.get(1)) ? scheduleCancellation.endDate : localDates.get(1);

            for (LocalDate date = cancellationStartDate; date.isBefore(cancellationEndDate) || date.isEqual(
                    cancellationEndDate); date = date.plusDays(1)) {
                trip.calendar.calendarDates.add(createCalendarDate(trip.serviceId, date, true));
            }

            log.trace("Creating cancellation trip from {}", scheduleCancellation);
            final Trip partialCancellationTrip = createTrip(schedule, cancellationStartDate, cancellationEndDate, TRIP_REPLACEMENT, timeTableRowsByTrainNumber, platformData);
            partialCancellationTrip.calendar.calendarDates.clear();

            final Map<Long, ScheduleRowPart> cancelledScheduleRowsMap = Maps.uniqueIndex(
                    Collections2.filter(scheduleCancellation.cancelledRows, Objects::nonNull), s -> s.id);

            final List<StopTime> removedStopTimes = new ArrayList<>();
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
        final var wholeDayCancellationsMap = new HashMap<String, ScheduleCancellation>();
        for (final ScheduleCancellation scheduleCancellation : schedule.scheduleCancellations) {
            if (scheduleCancellation.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY) {
                wholeDayCancellationsMap.put(String.format("%s_%s", scheduleCancellation.startDate, scheduleCancellation.endDate), scheduleCancellation);
            }
        }

        final var partialCancellations = Collections2.filter(schedule.scheduleCancellations,
                sc -> sc.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.PARTIALLY && !wholeDayCancellationsMap.containsKey(String.format("%s_%s", sc.startDate,sc.endDate)));
        var differentRouteCancellations = Collections2.filter(schedule.scheduleCancellations,
                sc -> sc.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE && !wholeDayCancellationsMap.containsKey(String.format("%s_%s", sc.startDate,sc.endDate)));

        if (differentRouteCancellations.size() > 1) {
            differentRouteCancellations = cancellationFlattener.flatten(differentRouteCancellations);
        }

        handleConnectedPartialCancellations(partialCancellations);
        return handleEqualDoubleCancellations(partialCancellations, differentRouteCancellations);
    }

    private Table<LocalDate, LocalDate, ScheduleCancellation> handleEqualDoubleCancellations(final Collection<ScheduleCancellation> partialCancellations, final Collection<ScheduleCancellation> differentRouteCancellations) {
        final Iterable<ScheduleCancellation> allCancellations = Iterables.concat(partialCancellations, differentRouteCancellations);
        final Table<LocalDate, LocalDate, ScheduleCancellation> cancellations = HashBasedTable.create();
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

    private void handleConnectedPartialCancellations(final Collection<ScheduleCancellation> partialCancellations) {
        for (final ScheduleCancellation left : partialCancellations) {
            final Range<LocalDate> leftRange = Range.closed(left.startDate, left.endDate);
            for (final ScheduleCancellation right : partialCancellations) {
                if (left != right) {
                    final Range<LocalDate> rightRange = Range.closed(right.startDate, right.endDate);
                    if (leftRange.isConnected(rightRange) && !leftRange.equals(rightRange)) {
                        final Range<LocalDate> newRange = leftRange.span(rightRange);

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
        final boolean arrivalExists = stopTime.source.arrival != null;
        final boolean departureExists = stopTime.source.departure != null;

        final boolean arrivalCancelled = arrivalExists && cancelledRows.containsKey(stopTime.source.arrival.id);
        final boolean departureCancelled = departureExists && cancelledRows.containsKey(stopTime.source.departure.id);

        return departureCancelled && !arrivalExists || arrivalCancelled && !departureExists || arrivalCancelled && departureCancelled;
    }

    private Trip createTrip(final Schedule schedule,
                            final LocalDate startDate,
                            final LocalDate endDate,
                            final String scheduleSuffix,
                            final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber,
                            final PlatformData platformData) {
        final String tripId = String.format("%s_%s%s",
                schedule.trainNumber,
                endDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                scheduleSuffix);
        final String serviceId = tripId;

        final Trip trip = new Trip(schedule);
        trip.serviceId = serviceId;
        trip.tripId = tripId;

        if (Strings.isNullOrEmpty(schedule.commuterLineId)) {
            trip.shortName = String.format("%s %s", schedule.trainType.name, schedule.trainNumber);
        } else {
            trip.shortName = String.format("%s (%s %s)", schedule.commuterLineId, schedule.trainType.name, schedule.trainNumber);
        }

        trip.calendar = createCalendar(schedule, serviceId, startDate, endDate);
        trip.calendar.calendarDates = createCalendarDatesFromExceptions(schedule, serviceId);
        trip.stopTimes = createStopTimes(schedule, tripId, timeTableRowsByTrainNumber, platformData);

        trip.wheelchair = getWheelchairAccessibility(schedule);
        trip.bikesAllowed = getBikesAllowed(schedule);

        return trip;
    }

    private Integer getWheelchairAccessibility(final Schedule s) {
        // at this time, we have no information about wheelchair accessibility
        return 0;
    }

    private Integer getBikesAllowed(final Schedule s) {
        // 0 or empty - No bike information for the trip.
        // 1 - Vehicle being used on this particular trip can accommodate at least one bicycle.
        // 2 - No bicycles are allowed on this trip.
        return switch (s.trainType.name) {
            case "PVS", "PVV", "MUS" -> 0;
            case "HL", "H", "P", "HDM", "IC2", "IC", "HSM", "PYO", "HLV" -> 1;
            case "S", "AE" -> 2;
            default ->

                // for all other types we leave it empty

                    null;
        };

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

    private boolean timeTableRowMatchesScheduleRow(final SimpleTimeTableRow simpleTimeTableRow, final ScheduleRow scheduleRow) {
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
    }

    private Optional<String> findTrack(final ScheduleRow scheduleRow, final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber) {
        final Long trainNumber = scheduleRow.schedule.trainNumber;

        return timeTableRowsByTrainNumber.getOrDefault(trainNumber, Collections.emptyList())
                .stream()
                .filter(simpleTimeTableRow -> simpleTimeTableRow.commercialTrack != null)
                .filter(simpleTimeTableRow -> timeTableRowMatchesScheduleRow(simpleTimeTableRow, scheduleRow))
                .map(matchingRow -> matchingRow.commercialTrack)
                .findAny();
    }

    private List<StopTime> createStopTimes(final Schedule schedule, final String tripId,
                                           final Map<Long, List<SimpleTimeTableRow>> timeTableRowsByTrainNumber,
                                           final PlatformData platformData) {
        final List<StopTime> stopTimes = new ArrayList<>();

        for (int i = 0; i < schedule.scheduleRows.size(); i++) {
            final ScheduleRow scheduleRow = schedule.scheduleRows.get(i);
            final StopTime stopTime = new StopTime(scheduleRow);
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

            final Optional<String> trackNumber = findTrack(scheduleRow, timeTableRowsByTrainNumber);
            if (trackNumber.isPresent()
                    && platformData.isValidTrack(scheduleRow.station.stationShortCode, trackNumber.get())) {
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

        stopTimes.getFirst().dropoffType = 1;
        Iterables.getLast(stopTimes).pickupType = 1;

        return stopTimes;
    }

    private List<CalendarDate> createCalendarDatesFromExceptions(final Schedule schedule, final String serviceId) {
        final List<CalendarDate> calendarDates = new ArrayList<>();
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
        final String key = String.format("%s_%s", serviceId, date.toString());
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

    private Boolean runOnDayToString(final Boolean runOnDay, final DayOfWeek dayOfWeek, final LocalDate departureDate) {
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
