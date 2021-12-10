package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Logic based on com.bedatadriven.jackson.datatype.jts.serialization.GeometrySerializer
public final class GeometryUtils {

    public static GeometryDto<?> fromJtsGeometry(com.vividsolutions.jts.geom.Geometry jtsGeometry) {
        switch (jtsGeometry.getGeometryType()) {
            case "Point":
                return toPoint((com.vividsolutions.jts.geom.Point) jtsGeometry);
            case "LineString":
                return toLineString((com.vividsolutions.jts.geom.LineString) jtsGeometry);
            case "MultiLineString":
                return toMultiLineString((com.vividsolutions.jts.geom.MultiLineString) jtsGeometry);
            case "Polygon":
                return toPolygon((com.vividsolutions.jts.geom.Polygon) jtsGeometry);
            case "GeometryCollection":
                return toGeometryCollection((com.vividsolutions.jts.geom.GeometryCollection) jtsGeometry);
            default:
                throw new IllegalArgumentException("Unknown geometry type: " + jtsGeometry.getGeometryType());
        }
    }

    private static GeometryCollectionDto toGeometryCollection(com.vividsolutions.jts.geom.GeometryCollection g) {
        List<GeometryDto<?>> geoms = new ArrayList<>();
        for (int i = 0; i < g.getNumGeometries(); i++) {
            geoms.add(fromJtsGeometry(g.getGeometryN(i)));
        }
        return new GeometryCollectionDto(geoms);
    }

    private static PolygonDto toPolygon(com.vividsolutions.jts.geom.Polygon p) {
        final List<com.vividsolutions.jts.geom.LineString> lines = new ArrayList<>();
        for(int i = 0; i < p.getNumInteriorRing(); ++i) {
            lines.add(p.getInteriorRingN(i));
        }
        lines.add(p.getExteriorRing());
        return new PolygonDto(lines.stream().map(GeometryUtils::toLineString).collect(Collectors.toList()));
    }

    private static MultiLineStringDto toMultiLineString(com.vividsolutions.jts.geom.MultiLineString mls) {
        final List<com.vividsolutions.jts.geom.LineString> lines = new ArrayList<>();
        for(int i = 0; i != mls.getNumGeometries(); ++i) {
            lines.add((com.vividsolutions.jts.geom.LineString) mls.getGeometryN(i));
        }
        return new MultiLineStringDto(lines.stream().map(GeometryUtils::toLineString).collect(Collectors.toList()));
    }

    private static LineStringDto toLineString(com.vividsolutions.jts.geom.LineString l) {
        final List<com.vividsolutions.jts.geom.Point> points = new ArrayList<>();
        for (int i = 0; i < l.getNumPoints(); i++) {
            points.add(l.getPointN(i));
        }
        return new LineStringDto(points.stream().map(GeometryUtils::toPoint).collect(Collectors.toList()));
    }

    private static PointDto toPoint(com.vividsolutions.jts.geom.Point p) {
        return new PointDto(p.getX(), p.getY());
    }

}
