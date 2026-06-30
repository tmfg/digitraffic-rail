package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

/**
 * Represents a NeTEx DayType with its weekday pattern.
 */
public class NeTExDayType {
    private final String id;
    private final String daysOfWeek;

    public NeTExDayType(final String id, final String daysOfWeek) {
        this.id = id;
        this.daysOfWeek = daysOfWeek;
    }

    public String getId() {
        return id;
    }

    public String getDaysOfWeek() {
        return daysOfWeek;
    }
}
