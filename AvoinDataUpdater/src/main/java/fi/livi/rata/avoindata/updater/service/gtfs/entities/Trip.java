package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import java.util.List;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

public class Trip extends GTFSEntity<Schedule> {
    public Calendar calendar;
    public List<StopTime> stopTimes;
    public String routeId;
    public String serviceId;
    public String tripId;

    public String shortName;
    public String headsign;

    public Integer shapeId;

    public Trip(final Schedule source) {
        super(source);
    }

    @Override
    public String toString() {
        return "Trip{" + "calendar=" + calendar + ", stopTimes=" + stopTimes
                .size() + ", routeId=" + routeId + ", serviceId='" + serviceId + '\'' + ", tripId='" + tripId + "\'}";
    }
}
