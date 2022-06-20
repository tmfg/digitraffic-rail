package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import org.locationtech.jts.geom.Geometry;

public class InfraApiPlatform {

    public final String liikennepaikkaId;
    public final String name;
    public final String description;
    public final String commercialTrack;
    public final Geometry geometry;

    public InfraApiPlatform(final String liikennepaikkaId, final String name, final String description, final String commercialTrack, final Geometry geometry) {
        this.liikennepaikkaId = liikennepaikkaId;
        this.name = name;
        this.description = description;
        this.commercialTrack = commercialTrack;
        this.geometry = geometry;
    }

}
