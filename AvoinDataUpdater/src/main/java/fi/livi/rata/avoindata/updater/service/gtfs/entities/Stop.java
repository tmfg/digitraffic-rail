package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

public class Stop extends GTFSEntity<Station> {
    public String stopId;
    public String stopCode;
    public String name;
    public String description;
    public double latitude;
    public double longitude;
    public int locationType;

    public Stop(final Station source) {
        super(source);
    }

    public Stop(final Station source, final String stopId, final String stopCode, final String name, final double latitude, final double longitude, final int locationType) {
        super(source);
        this.stopId = stopId;
        this.stopCode = stopCode;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationType = locationType;
    }


}
