package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

public class Stop extends GTFSEntity<Station> {
    public String stopId;
    public String stopCode;
    public String name;
    public String track;
    public double latitude;
    public double longitude;

    public Stop(final Station source) {
        super(source);
    }
}
