package fi.livi.rata.avoindata.updater.service.timetable.entities;


import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;

public class ScheduleRow {
    public enum ScheduleRowStopType {
        PASS,
        COMMERCIAL,
        NONCOMMERCIAL
    }

    public long id;

    public ScheduleRowPart departure;
    public ScheduleRowPart arrival;

    public Schedule schedule;

    public StationEmbeddable station;
    public String commercialTrack;


    @Override
    public String toString() {
        return "ScheduleRow{" +
                "id=" + id +
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

        final ScheduleRow that = (ScheduleRow) o;

        return id == that.id;

    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
