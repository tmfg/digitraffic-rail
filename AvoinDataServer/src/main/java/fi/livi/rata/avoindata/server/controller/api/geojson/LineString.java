package fi.livi.rata.avoindata.server.controller.api.geojson;

import java.util.List;
import java.util.stream.Collectors;

public final class LineString implements Geometry<List<List<Double>>> {

    private final List<Point> points;

    public LineString(final List<Point> points) {
        this.points = points;
    }

    @Override
    public List<List<Double>> getCoordinates() {
        return points.stream().map(Point::getCoordinates).collect(Collectors.toList());
    }

    @Override
    public String getType() {
        return "LineString";
    }
}
