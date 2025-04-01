package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import java.time.Duration;

import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

public class StopTime extends GTFSEntity<ScheduleRow> {
    public String tripId;
    public Duration arrivalTime;
    public Duration departureTime;
    public String stopId;
    public int stopSequence;
    public int pickupType;
    public int dropoffType;
    public String track;

    public StopTime(final ScheduleRow source) {
        super(source);
    }

    public String getStopCodeWithPlatform() {
        return track != null ? stopId + "_" + track : stopId + "_0";
    }

    @Override
    public String toString() {
        return "StopTime{stopId=" + stopId + ",arrivalTime=" + arrivalTime + ", departureTime=" + departureTime + '}';
    }
}

