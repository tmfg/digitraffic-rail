package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

/**
 * Represents a NeTEx DayTypeAssignment linking a DayType to either an
 * OperatingPeriod or a specific Date.
 */
public class NeTExDayTypeAssignment {
    private final String dayTypeId;
    private final String operatingPeriodId; // null for ADHOC
    private final LocalDate date; // null for REGULAR

    /**
     * Constructor for REGULAR schedules (linked to OperatingPeriod).
     */
    public static NeTExDayTypeAssignment forOperatingPeriod(final String dayTypeId, final String operatingPeriodId) {
        return new NeTExDayTypeAssignment(dayTypeId, operatingPeriodId, null);
    }

    /**
     * Constructor for ADHOC schedules (specific date).
     */
    public static NeTExDayTypeAssignment forDate(final String dayTypeId, final LocalDate date) {
        return new NeTExDayTypeAssignment(dayTypeId, null, date);
    }

    private NeTExDayTypeAssignment(final String dayTypeId, final String operatingPeriodId, final LocalDate date) {
        this.dayTypeId = dayTypeId;
        this.operatingPeriodId = operatingPeriodId;
        this.date = date;
    }

    public String getDayTypeId() {
        return dayTypeId;
    }

    public String getOperatingPeriodId() {
        return operatingPeriodId;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isDateBased() {
        return date != null;
    }
}
