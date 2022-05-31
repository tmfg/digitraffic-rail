package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import org.locationtech.jts.geom.Geometry;

public class InfraApiPlatform {

    public final String liikennepaikkaId;
    public final String name;
    public final String description;
    public final String commercialTrack;
    public final Geometry geometry;

    public InfraApiPlatform(String liikennepaikkaId, String name, String description, String commercialTrack, Geometry geometry) {
        this.liikennepaikkaId = liikennepaikkaId;
        this.name = name;
        this.description = description;
        this.commercialTrack = commercialTrack;
        this.geometry = geometry;
    }

    @Override
    public String toString() {
        return "InfraApiPlatform{" +
                "liikennepaikkaId='" + liikennepaikkaId + '\'' +
                ", platformName='" + name + '\'' +
                ", description='" + description + '\'' +
                ", commercialTrack='" + commercialTrack + '\'' +
                ", geometry=" + geometry +
                ", centroid=" + geometry.getCentroid() +
                '}';
    }
}
