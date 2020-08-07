package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateUtils;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

@Service
public class ScheduleToTrainConverter {
    private Logger log = LoggerFactory.getLogger(ScheduleToTrainConverter.class);

    @Autowired
    private DateUtils du;
    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;


    public Map<Train, Schedule> extractSchedules(final List<Schedule> schedules, final LocalDate extractedDate) {
        Map<Train, Schedule> output = new HashMap<>();
        for (final Schedule schedule : schedules) {
            log.trace("Trying to extract {} for date {}", schedule, extractedDate);
            if (schedule.isRunOnDay(extractedDate)) {
                output.put(createTrain(schedule, extractedDate), schedule);
            }
        }

        return output;
    }

    private Train createTrain(final Schedule schedule, final LocalDate extractedDate) {
        Train train = new Train(schedule.trainNumber, extractedDate, schedule.operator.operatorUICCode, schedule.operator.operatorShortCode,
                schedule.trainCategory.id, schedule.trainType.id, schedule.commuterLineId, false,
                isScheduleCancelled(schedule, extractedDate), schedule.version, schedule.timetableType, schedule.acceptanceDate);

        for (final ScheduleRow scheduleRow : schedule.scheduleRows) {
            if (scheduleRow.arrival != null && !isScheduleRowPartCancelled(extractedDate, schedule, scheduleRow.arrival,
                    ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE)) {
                train.timeTableRows.add(createTimeTableRow(schedule, extractedDate, scheduleRow, scheduleRow.arrival,
                        TimeTableRow.TimeTableRowType.ARRIVAL));
            }

            if (scheduleRow.departure != null && !isScheduleRowPartCancelled(extractedDate, schedule, scheduleRow.departure,
                    ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE)) {
                train.timeTableRows.add(createTimeTableRow(schedule, extractedDate, scheduleRow, scheduleRow.departure,
                        TimeTableRow.TimeTableRowType.DEPARTURE));
            }
        }

        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.train = train;
        }

        train.timeTableRows = sortTimeTableRows(train.timeTableRows);

        emptyCommercialTrackInTimeTableRows(train.timeTableRows);

        initializeIsTrainStoppingInformation(train.timeTableRows);

        return train;
    }

    private List<TimeTableRow> sortTimeTableRows(final List<TimeTableRow> timeTableRows) {
        return timeTableRows.stream().sorted((o1, o2) -> {
            final int i = o1.scheduledTime.compareTo(o2.scheduledTime);
            if (i == 0) {
                return o1.type.compareTo(o2.type);
            } else {
                return i;
            }
        }).collect(Collectors.toList());
    }

    private boolean isScheduleCancelled(final Schedule schedule, LocalDate extractDate) {
        return schedule.changeType.equals("P") || isLimitedByScheduleExceptions(schedule, extractDate);
    }

    private boolean isLimitedByScheduleExceptions(Schedule schedule, final LocalDate extractedDate) {
        for (final ScheduleException exception : schedule.scheduleExceptions) {
            if (exception.date.equals(extractedDate) && !exception.isRun) {
                return true;
            }
        }

        return false;
    }

    private TimeTableRow createTimeTableRow(final Schedule schedule, final LocalDate extractedDate, final ScheduleRow scheduleRow,
            final ScheduleRowPart scheduleRowPart, final TimeTableRow.TimeTableRowType timeTableRowType) {
        boolean isCancelled = isScheduleRowPartCancelled(extractedDate, schedule, scheduleRowPart,
                ScheduleCancellation.ScheduleCancellationType.PARTIALLY);

        final ScheduleRowPart firstDeparture = schedule.scheduleRows.get(0).departure;
        ZonedDateTime departureTime = ZonedDateTime.of(extractedDate, LocalTime.of(0, 0).plus(firstDeparture.timestamp),
                ZoneId.of("Europe/Helsinki"));
        Duration fromStart = scheduleRowPart.timestamp.minus(firstDeparture.timestamp);

        ZonedDateTime timestamp = departureTime.plus(fromStart);

        TimeTableRow timeTableRow = new TimeTableRow(scheduleRow.station.stationShortCode, scheduleRow.station.stationUICCode,
                scheduleRow.station.countryCode, timeTableRowType, null, isCancelled, timestamp, null, null, null, scheduleRowPart.id,
                schedule.trainNumber, extractedDate, scheduleRowPart.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL, 0L,
                new HashSet<>(), null);

        return timeTableRow;
    }


    private boolean isScheduleRowPartCancelled(final LocalDate extractedDate, final Schedule schedule,
            final ScheduleRowPart scheduleRowPart, final ScheduleCancellation.ScheduleCancellationType cancellationType) {
        if (isScheduleCancelled(schedule, extractedDate)) {
            return true;
        }

        for (final ScheduleCancellation scheduleCancellation : schedule.scheduleCancellations) {
            final boolean isBetween = du.isInclusivelyBetween(extractedDate, scheduleCancellation.startDate, scheduleCancellation.endDate);
            if (isBetween && scheduleCancellation.scheduleCancellationType == cancellationType) {
                if (scheduleCancellation.cancelledRows.contains(scheduleRowPart)) {
                    return true;
                }
            }
        }
        return false;
    }


    private void initializeIsTrainStoppingInformation(final List<TimeTableRow> sortedTimeTableRows) {
        if (sortedTimeTableRows == null || sortedTimeTableRows.isEmpty()) {
            return;
        }

        for (int i = 0; i < sortedTimeTableRows.size() - 1; i++) {
            final TimeTableRow current = sortedTimeTableRows.get(i);
            final TimeTableRow next = sortedTimeTableRows.get(i + 1);

            if (current.station.stationUICCode == next.station.stationUICCode) {
                if (current.scheduledTime.isEqual(next.scheduledTime)) {
                    current.trainStopping = false;
                    current.commercialStop = null;
                    next.trainStopping = false;
                    next.commercialStop = null;
                } else {
                    current.commercialStop = next.commercialStop;
                }
            }
        }


        sortedTimeTableRows.get(0).commercialStop = true;
        sortedTimeTableRows.get(sortedTimeTableRows.size() - 1).commercialStop = true;
    }


    private void emptyCommercialTrackInTimeTableRows(final List<TimeTableRow> sortedTimeTableRows) {
        for (int i = 0; i < sortedTimeTableRows.size(); i++) {
            if (i == 0 || i == sortedTimeTableRows.size() - 1) {
                final TimeTableRow current = sortedTimeTableRows.get(i);
                if (current.cancelled) {
                    current.commercialTrack = "";
                }
            } else {
                final TimeTableRow current = sortedTimeTableRows.get(i);
                final TimeTableRow next = sortedTimeTableRows.get(i + 1);

                if (current.cancelled || current.train.cancelled) {
                    current.commercialTrack = "";
                } else if (shouldCommercialTrackBeEmptied(current, next)) {
                    current.commercialTrack = "";
                    next.commercialTrack = "";
                }
            }
        }
    }

    private boolean shouldCommercialTrackBeEmptied(final TimeTableRow current, final TimeTableRow next) {
        return current.type == TimeTableRow.TimeTableRowType.ARRIVAL && current.station.stationUICCode == next.station.stationUICCode &&
                !current.cancelled && !next.cancelled && current.scheduledTime
                .equals(next.scheduledTime);
    }
}
