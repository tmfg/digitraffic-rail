package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

public class Platform extends Stop {

    public final String track;

    public Platform(final Station source, final String stopId, final String stopCode, final String name, final double latitude, final double longitude, final String track) {
        super(source, stopId, stopCode, name, latitude, longitude, 0);
        this.track = track;
    }
}
