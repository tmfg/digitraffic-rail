package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Calendar {
    public List<CalendarDate> calendarDates = new ArrayList<>();
    public String serviceId;
    public boolean monday;
    public boolean tuesday;
    public boolean wednesday;
    public boolean thursday;
    public boolean friday;
    public boolean saturday;
    public boolean sunday;
    public LocalDate startDate;
    public LocalDate endDate;

    @Override
    public String toString() {
        return "Calendar{" + "calendarDates=" + calendarDates.size() + ", serviceId='" + serviceId + '\'' + ", startDate=" + startDate + ", " +
                "endDate=" + endDate + '}';
    }
}
