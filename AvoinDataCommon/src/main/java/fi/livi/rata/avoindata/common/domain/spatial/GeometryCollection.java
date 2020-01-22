package fi.livi.rata.avoindata.common.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.stream.Collectors;

public final class GeometryCollection implements Geometry<List<?>> {

    @JsonIgnore
    public final List<Geometry<?>> geometries;

    public GeometryCollection(List<Geometry<?>> geometries) {
        this.geometries = geometries;
    }

    @Override
    public List<?> getCoordinates() {
        return geometries.stream().map(Geometry::getCoordinates).collect(Collectors.toList());
    }

}
