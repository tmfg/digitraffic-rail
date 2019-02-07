package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import com.google.common.base.Objects;

import java.time.LocalDate;

public class CalendarDate {
    public int exceptionType;
    public LocalDate date;
    public String serviceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarDate that = (CalendarDate) o;
        return exceptionType == that.exceptionType &&
                Objects.equal(date, that.date) &&
                Objects.equal(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(exceptionType, date, serviceId);
    }
}
