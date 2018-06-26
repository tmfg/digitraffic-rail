package fi.livi.rata.avoindata.updater.service.timetable.entities;


import java.time.LocalDate;

public class ScheduleException {
    public long id;

    public boolean isRun;
    public LocalDate date;

    @Override
    public String toString() {
        return "ScheduleException{" +
                "id=" + id +
                ", isRun=" + isRun +
                ", date=" + date +
                '}';
    }
}
