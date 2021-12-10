package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

// Logic based on com.bedatadriven.jackson.datatype.jts.serialization.GeometrySerializer
public final class GeometryUtils {

    public static GeometryDto<?> fromJtsGeometry(Geometry jtsGeometry) {
        switch (jtsGeometry.getGeometryType()) {
            case "Point":
                return toPoint((Point) jtsGeometry);
            case "LineString":
                return toLineString((LineString) jtsGeometry);
            case "MultiLineString":
                return toMultiLineString((MultiLineString) jtsGeometry);
            case "Polygon":
                return toPolygon((Polygon) jtsGeometry);
            case "GeometryCollection":
                return toGeometryCollection((GeometryCollection) jtsGeometry);
            default:
                throw new IllegalArgumentException("Unknown geometry type: " + jtsGeometry.getGeometryType());
        }
    }

    private static GeometryCollectionDto toGeometryCollection(GeometryCollection g) {
        List<GeometryDto<?>> geoms = new ArrayList<>();
        for (int i = 0; i < g.getNumGeometries(); i++) {
            geoms.add(fromJtsGeometry(g.getGeometryN(i)));
        }
        return new GeometryCollectionDto(geoms);
    }

    private static PolygonDto toPolygon(Polygon p) {
        final List<LineString> lines = new ArrayList<>();
        for (int i = 0; i < p.getNumInteriorRing(); ++i) {
            lines.add(p.getInteriorRingN(i));
        }
        lines.add(p.getExteriorRing());
        return new PolygonDto(lines.stream().map(GeometryUtils::toLineString).collect(Collectors.toList()));
    }

    private static MultiLineStringDto toMultiLineString(MultiLineString mls) {
        final List<LineString> lines = new ArrayList<>();
        for (int i = 0; i != mls.getNumGeometries(); ++i) {
            lines.add((LineString) mls.getGeometryN(i));
        }
        return new MultiLineStringDto(lines.stream().map(GeometryUtils::toLineString).collect(Collectors.toList()));
    }

    private static LineStringDto toLineString(LineString l) {
        final List<Point> points = new ArrayList<>();
        for (int i = 0; i < l.getNumPoints(); i++) {
            points.add(l.getPointN(i));
        }
        return new LineStringDto(points.stream().map(GeometryUtils::toPoint).collect(Collectors.toList()));
    }

    private static PointDto toPoint(Point p) {
        return new PointDto(p.getX(), p.getY());
    }

}
