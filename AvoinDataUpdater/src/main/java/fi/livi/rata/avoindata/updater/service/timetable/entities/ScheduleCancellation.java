package fi.livi.rata.avoindata.updater.service.timetable.entities;


import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ScheduleCancellation {
    public long id;

    public LocalDate startDate;
    public LocalDate endDate;

    public ScheduleCancellationType scheduleCancellationType;

    public Set<ScheduleRowPart> cancelledRows = new HashSet<>();

    public enum ScheduleCancellationType {
        WHOLE_DAY,
        PARTIALLY,
        DIFFERENT_ROUTE
    }

    @Override
    public String toString() {
        return "ScheduleCancellation{" + "id=" + id + ", startDate=" + startDate + ", endDate=" + endDate + ", scheduleCancellationType="
                + scheduleCancellationType + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleCancellation that = (ScheduleCancellation) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
