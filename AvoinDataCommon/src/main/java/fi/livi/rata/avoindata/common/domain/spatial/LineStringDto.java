package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.List;
import java.util.stream.Collectors;

public final class LineStringDto implements GeometryDto<List<List<Double>>> {

    private final List<PointDto> points;

    public LineStringDto(final List<PointDto> points) {
        this.points = points;
    }

    @Override
    public List<List<Double>> getCoordinates() {
        return points.stream().map(PointDto::getCoordinates).collect(Collectors.toList());
    }

    @Override
    public String getType() {
        return "LineString";
    }
}
