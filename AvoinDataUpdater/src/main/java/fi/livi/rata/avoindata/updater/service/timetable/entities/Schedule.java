package fi.livi.rata.avoindata.updater.service.timetable.entities;


import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

public class Schedule {
    public long id;

    public List<ScheduleRow> scheduleRows;
    public Set<ScheduleCancellation> scheduleCancellations;
    public Set<ScheduleException> scheduleExceptions;

    public LocalDate startDate;
    public LocalDate endDate;

    public Long trainNumber;
    public Long version;

    public Operator operator;
    public TrainCategory trainCategory;
    public TrainType trainType;

    public Train.TimetableType timetableType;
    public ZonedDateTime acceptanceDate;
    public LocalDate effectiveFrom;
    public String commuterLineId;

    public Boolean runOnMonday;
    public Boolean runOnTuesday;
    public Boolean runOnWednesday;
    public Boolean runOnThursday;
    public Boolean runOnFriday;
    public Boolean runOnSaturday;
    public Boolean runOnSunday;
    public String typeCode;
    public String changeType;
    public String capacityId;

    public boolean isRunOnDay(final LocalDate extractedDate) {
        if (isLimitedByWholeDayCancellations(extractedDate) || isLimitedByExceptions(extractedDate)) {
            return false;
        }


        return isAllowedByDates(extractedDate);
    }

    private boolean isLimitedByExceptions(final LocalDate extractedDate) {
        for (final ScheduleException scheduleException : scheduleExceptions) {
            final boolean isCancelledOnExtractedDate = extractedDate.equals(scheduleException.date) && !scheduleException.isRun;
            if (isCancelledOnExtractedDate) {
                return true;
            }
        }
        return false;
    }

    public boolean isAllowedByDates(final LocalDate extractedDate) {
        if (typeCode.equals("Y") || timetableType == Train.TimetableType.ADHOC) {
            return startDate.equals(extractedDate);
        }

        if (effectiveFrom != null && effectiveFrom.isAfter(extractedDate)) {
            return false;
        }

        for (final ScheduleException scheduleException : scheduleExceptions) {
            if (scheduleException.date.equals(extractedDate)) {
                return scheduleException.isRun;
            }
        }

        final boolean isBetweenStartAndEnd = isBetweenStartAndEnd(extractedDate);
        if (!isBetweenStartAndEnd) {
            return false;
        }

        if (isLimitedByWeekday(extractedDate)) {
            return false;
        }

        return true;
    }

    public boolean isBetweenStartAndEnd(final LocalDate date) {
        return DateUtils.isInclusivelyBetween(date, startDate, endDate);
    }

    private boolean isLimitedByWholeDayCancellations(final LocalDate extractedDate) {
        for (final ScheduleCancellation scheduleCancellation : scheduleCancellations) {
            final boolean isCancelledOnExtractedDate = DateUtils.isInclusivelyBetween(extractedDate, scheduleCancellation.startDate,
                    scheduleCancellation.endDate);
            if (isCancelledOnExtractedDate && scheduleCancellation.scheduleCancellationType == ScheduleCancellation
                    .ScheduleCancellationType.WHOLE_DAY) {
                return true;
            }
        }
        return false;
    }

    private boolean isLimitedByWeekday(final LocalDate extractedDate) {
        final DayOfWeek dayOfWeek = extractedDate.getDayOfWeek();

        if (dayOfWeek == DayOfWeek.MONDAY && !runOnMonday) {
            return true;
        } else if (dayOfWeek == DayOfWeek.TUESDAY && !runOnTuesday) {
            return true;
        } else if (dayOfWeek == DayOfWeek.WEDNESDAY && !runOnWednesday) {
            return true;
        } else if (dayOfWeek == DayOfWeek.THURSDAY && !runOnThursday) {
            return true;
        } else if (dayOfWeek == DayOfWeek.FRIDAY && !runOnFriday) {
            return true;
        } else if (dayOfWeek == DayOfWeek.SATURDAY && !runOnSaturday) {
            return true;
        } else if (dayOfWeek == DayOfWeek.SUNDAY && !runOnSunday) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Schedule{" +
                "trainNumber=" + trainNumber +
                ", id=" + id +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", timetableType=" + timetableType +
                ", typeCode=" + typeCode +
                ", version=" + version +
                '}';
    }
}
