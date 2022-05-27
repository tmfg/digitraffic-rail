package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import org.locationtech.jts.geom.Geometry;

public class InfraApiPlatform {

    public String liikennepaikkaId;
    public String name;
    public String description;
    public String commercialTrack;
    public Geometry geometry;

    public InfraApiPlatform() {
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
