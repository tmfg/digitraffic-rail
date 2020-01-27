package fi.livi.rata.avoindata.common.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.stream.Collectors;

public final class GeometryCollectionDto implements GeometryDto<List<?>> {

    @JsonIgnore
    public final List<GeometryDto<?>> geometries;

    public GeometryCollectionDto(List<GeometryDto<?>> geometries) {
        this.geometries = geometries;
    }

    @Override
    public List<?> getCoordinates() {
        return geometries.stream().map(GeometryDto::getCoordinates).collect(Collectors.toList());
    }

    @Override
    public String getType() {
        return "GeometryCollection";
    }
}
