package fi.livi.rata.avoindata.updater.service.netex;

import java.util.List;
import java.util.Map;

/**
 * Holds the calendar data produced by NeTExCalendarService.
 * Contains DayTypes, OperatingPeriods, and DayTypeAssignments mapped by their IDs.
 */
public class NeTExCalendarData {

    private final List<NeTExDayType> dayTypes;
    private final List<NeTExOperatingPeriod> operatingPeriods;
    private final List<NeTExDayTypeAssignment> dayTypeAssignments;
    private final Map<Long, String> scheduleToDayTypeId;

    public NeTExCalendarData(
            final List<NeTExDayType> dayTypes,
            final List<NeTExOperatingPeriod> operatingPeriods,
            final List<NeTExDayTypeAssignment> dayTypeAssignments,
            final Map<Long, String> scheduleToDayTypeId) {
        this.dayTypes = dayTypes;
        this.operatingPeriods = operatingPeriods;
        this.dayTypeAssignments = dayTypeAssignments;
        this.scheduleToDayTypeId = scheduleToDayTypeId;
    }

    public List<NeTExDayType> getDayTypes() {
        return dayTypes;
    }

    public List<NeTExOperatingPeriod> getOperatingPeriods() {
        return operatingPeriods;
    }

    public List<NeTExDayTypeAssignment> getDayTypeAssignments() {
        return dayTypeAssignments;
    }

    /**
     * Returns the DayType ID assigned to a given schedule (by schedule ID).
     */
    public String getDayTypeIdForSchedule(final long scheduleId) {
        return scheduleToDayTypeId.get(scheduleId);
    }
}
