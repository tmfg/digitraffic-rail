package fi.livi.rata.avoindata.server.controller.api.geojson;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public final class Point implements Geometry<List<Double>> {

    @JsonIgnore
    public final double longitude;
    @JsonIgnore
    public final double latitude;

    public Point(final double longitude, final double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    @Override
    public List<Double> getCoordinates() {
        return List.of(longitude, latitude);
    }

    @Override
    public String getType() {
        return "Point";
    }
}
