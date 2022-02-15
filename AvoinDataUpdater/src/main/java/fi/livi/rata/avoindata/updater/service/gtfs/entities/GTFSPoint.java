package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import java.util.Objects;

public class GTFSPoint {
    public double latitude;
    public double longitude;

    public GTFSPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GTFSPoint gtfsPoint = (GTFSPoint) o;
        return Double.compare(gtfsPoint.latitude, latitude) == 0 && Double.compare(gtfsPoint.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }
}
