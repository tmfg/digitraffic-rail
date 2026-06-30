package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

import java.util.List;

/**
 * Converts Schedule date ranges and weekday masks into NeTEx calendar structures:
 * DayType, OperatingPeriod, and DayTypeAssignment.
 */
public class NeTExCalendarService {

    private final NeTExIdGenerator idGenerator;

    public NeTExCalendarService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Creates calendar data (DayTypes, OperatingPeriods, DayTypeAssignments) from schedules.
     * Deduplicates DayTypes with the same weekday pattern and date range.
     */
    public NeTExCalendarData createCalendarData(final List<Schedule> schedules) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Creates a DaysOfWeek string from the schedule's weekday flags.
     * E.g., "Monday Tuesday Wednesday Thursday Friday"
     */
    public String createDaysOfWeekString(final Schedule schedule) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Generates a deterministic hash for a DayType based on weekday pattern and date range.
     */
    public String generateDayTypeHash(final Schedule schedule) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
