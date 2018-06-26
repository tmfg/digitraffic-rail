package fi.livi.rata.avoindata.updater.service.timetable.entities;


import java.time.Duration;

public class ScheduleRowPart {
    public long id;
    public Duration timestamp;
    public ScheduleRow.ScheduleRowStopType stopType;

    public ScheduleRow scheduleRow;

    @Override
    public String toString() {
        return "ScheduleRowPart{" +
                "scheduleRow=" + scheduleRow +
                ", timestamp=" + timestamp +
                ", id=" + id +
                ", stopType=" + stopType +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ScheduleRowPart that = (ScheduleRowPart) o;

        return id == that.id;

    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
