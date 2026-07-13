package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Converts Schedule date ranges and weekday masks into NeTEx calendar
 * structures:
 * DayType, OperatingPeriod, and DayTypeAssignment.
 */
@Service
public class NeTExCalendarService {

    private final NeTExIdGenerator idGenerator;

    public NeTExCalendarService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Creates calendar data (DayTypes, OperatingPeriods, DayTypeAssignments) from
     * schedules.
     * Deduplicates DayTypes with the same weekday pattern and date range.
     */
    public NeTExCalendarData createCalendarData(final List<Schedule> schedules) {
        final Map<String, NeTExDayType> dayTypeMap = new LinkedHashMap<>();
        final Map<String, NeTExOperatingPeriod> operatingPeriodMap = new LinkedHashMap<>();
        final List<NeTExDayTypeAssignment> dayTypeAssignments = new ArrayList<>();
        final Map<Long, String> scheduleToDayTypeId = new HashMap<>();

        for (final Schedule schedule : schedules) {
            final String hash = generateDayTypeHash(schedule);
            final String dayTypeId = idGenerator.dayTypeId(hash);

            if (!dayTypeMap.containsKey(dayTypeId)) {
                dayTypeMap.put(dayTypeId, new NeTExDayType(dayTypeId, createDaysOfWeekString(schedule)));
            }

            scheduleToDayTypeId.put(schedule.id, dayTypeId);

            if (schedule.timetableType == Train.TimetableType.ADHOC || schedule.endDate == null) {
                dayTypeAssignments.add(NeTExDayTypeAssignment.forDate(dayTypeId, schedule.startDate));
            } else {
                final String periodId = idGenerator.operatingPeriodId(schedule.startDate + "-" + schedule.endDate);
                if (!operatingPeriodMap.containsKey(periodId)) {
                    operatingPeriodMap.put(periodId,
                            new NeTExOperatingPeriod(periodId, schedule.startDate, schedule.endDate));
                }
                dayTypeAssignments.add(NeTExDayTypeAssignment.forOperatingPeriod(dayTypeId, periodId));
            }
        }

        return new NeTExCalendarData(
                new ArrayList<>(dayTypeMap.values()),
                new ArrayList<>(operatingPeriodMap.values()),
                dayTypeAssignments,
                scheduleToDayTypeId);
    }

    /**
     * Creates a DaysOfWeek string from the schedule's weekday flags.
     */
    public String createDaysOfWeekString(final Schedule schedule) {
        final List<String> days = new ArrayList<>();
        if (Boolean.TRUE.equals(schedule.runOnMonday))
            days.add("Monday");
        if (Boolean.TRUE.equals(schedule.runOnTuesday))
            days.add("Tuesday");
        if (Boolean.TRUE.equals(schedule.runOnWednesday))
            days.add("Wednesday");
        if (Boolean.TRUE.equals(schedule.runOnThursday))
            days.add("Thursday");
        if (Boolean.TRUE.equals(schedule.runOnFriday))
            days.add("Friday");
        if (Boolean.TRUE.equals(schedule.runOnSaturday))
            days.add("Saturday");
        if (Boolean.TRUE.equals(schedule.runOnSunday))
            days.add("Sunday");
        return String.join(" ", days);
    }

    /**
     * Generates a deterministic hash for a DayType based on weekday pattern and
     * date range.
     */
    public String generateDayTypeHash(final Schedule schedule) {
        final StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(schedule.runOnMonday))
            sb.append("Mo");
        if (Boolean.TRUE.equals(schedule.runOnTuesday))
            sb.append("Tu");
        if (Boolean.TRUE.equals(schedule.runOnWednesday))
            sb.append("We");
        if (Boolean.TRUE.equals(schedule.runOnThursday))
            sb.append("Th");
        if (Boolean.TRUE.equals(schedule.runOnFriday))
            sb.append("Fr");
        if (Boolean.TRUE.equals(schedule.runOnSaturday))
            sb.append("Sa");
        if (Boolean.TRUE.equals(schedule.runOnSunday))
            sb.append("Su");
        sb.append("-").append(schedule.startDate).append("-").append(schedule.endDate);
        return sb.toString();
    }
}
