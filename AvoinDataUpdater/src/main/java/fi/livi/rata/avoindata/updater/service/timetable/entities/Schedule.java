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

    /**
     * Set only on a copy made for days when some stops are cancelled; tells that copy apart from the
     * original schedule, which keeps the same {@link #id}.
     */
    public String partialCancellationKey;

    /**
     * Set only on a copy made for cancelled stops: all rows of the original schedule, cancelled ones included.
     * The times past midnight are counted from its first departure, and SIRI needs the planned tracks of the
     * cancelled stops too, since it still sees them on the real train.
     */
    public List<ScheduleRow> originalRows;

    /**
     * Set only on a copy made for cancelled stops: the rows the real train has on the days the copy is used.
     * That is all the original rows except the ones removed by a different-route cancellation, which the real
     * train does not have at all. Stops removed by a partial cancellation stay, because the real train still
     * has them, marked cancelled. SIRI counts station visits over these rows.
     */
    public List<ScheduleRow> realtimeRows;

    /** Stable id of this schedule or its partial-cancellation copy. */
    public String journeyKey() {
        return partialCancellationKey == null ? String.valueOf(id) : id + "-" + partialCancellationKey;
    }

    public Schedule copyWithRows(final List<ScheduleRow> rows, final String cancellationKey) {
        final Schedule copy = new Schedule();
        copy.id = id;
        copy.scheduleRows = rows;
        copy.scheduleCancellations = scheduleCancellations;
        copy.scheduleExceptions = scheduleExceptions;
        copy.startDate = startDate;
        copy.endDate = endDate;
        copy.trainNumber = trainNumber;
        copy.version = version;
        copy.operator = operator;
        copy.trainCategory = trainCategory;
        copy.trainType = trainType;
        copy.timetableType = timetableType;
        copy.acceptanceDate = acceptanceDate;
        copy.effectiveFrom = effectiveFrom;
        copy.commuterLineId = commuterLineId;
        copy.runOnMonday = runOnMonday;
        copy.runOnTuesday = runOnTuesday;
        copy.runOnWednesday = runOnWednesday;
        copy.runOnThursday = runOnThursday;
        copy.runOnFriday = runOnFriday;
        copy.runOnSaturday = runOnSaturday;
        copy.runOnSunday = runOnSunday;
        copy.typeCode = typeCode;
        copy.changeType = changeType;
        copy.capacityId = capacityId;
        copy.partialCancellationKey = cancellationKey;
        return copy;
    }

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

        return isBetweenStartAndEnd(extractedDate) && !isLimitedByWeekday(extractedDate);
    }

    public boolean isBetweenStartAndEnd(final LocalDate date) {
        return DateUtils.isInclusivelyBetween(date, startDate, endDate);
    }

    private boolean isLimitedByWholeDayCancellations(final LocalDate extractedDate) {
        for (final ScheduleCancellation scheduleCancellation : scheduleCancellations) {
            if(scheduleCancellation.scheduleCancellationType == ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY
                && DateUtils.isInclusivelyBetween(extractedDate, scheduleCancellation.startDate,
                    scheduleCancellation.endDate)) {
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
