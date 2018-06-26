package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

import java.time.Duration;

public class StopTime extends GTFSEntity<ScheduleRow> {
    public String tripId;
    public Duration arrivalTime;
    public Duration departureTime;
    public String stopId;
    public int stopSequence;
    public int pickupType;
    public int dropoffType;

    public StopTime(final ScheduleRow source) {
        super(source);
    }

    @Override
    public String toString() {
        return "StopTime{stopId=" + stopId + ",arrivalTime=" + arrivalTime + ", departureTime=" + departureTime + '}';
    }
}

