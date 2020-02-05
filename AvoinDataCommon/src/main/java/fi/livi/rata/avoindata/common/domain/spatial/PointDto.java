package fi.livi.rata.avoindata.common.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public final class PointDto implements GeometryDto<List<Double>> {

    @JsonIgnore
    public final double longitude;
    @JsonIgnore
    public final double latitude;

    public PointDto(final double longitude, final double latitude) {
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
