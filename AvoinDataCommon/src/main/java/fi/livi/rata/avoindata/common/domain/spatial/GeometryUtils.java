package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Logic based on com.bedatadriven.jackson.datatype.jts.serialization.GeometrySerializer
public final class GeometryUtils {

    public static Geometry<?> fromJtsGeometry(com.vividsolutions.jts.geom.Geometry jtsGeometry) {
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

    private static GeometryCollection toGeometryCollection(com.vividsolutions.jts.geom.GeometryCollection g) {
        List<Geometry<?>> geoms = new ArrayList<>();
        for (int i = 0; i < g.getNumGeometries(); i++) {
            geoms.add(fromJtsGeometry(g.getGeometryN(i)));
        }
        return new GeometryCollection(geoms);
    }

    private static Polygon toPolygon(com.vividsolutions.jts.geom.Polygon p) {
        final List<com.vividsolutions.jts.geom.LineString> lines = new ArrayList<>();
        for(int i = 0; i < p.getNumInteriorRing(); ++i) {
            lines.add(p.getInteriorRingN(i));
        }
        lines.add(p.getExteriorRing());
        return new Polygon(lines.stream().map(GeometryUtils::toLineString).collect(Collectors.toList()));
    }

    private static MultiLineString toMultiLineString(com.vividsolutions.jts.geom.MultiLineString mls) {
        final List<com.vividsolutions.jts.geom.LineString> lines = new ArrayList<>();
        for(int i = 0; i != mls.getNumGeometries(); ++i) {
            lines.add((com.vividsolutions.jts.geom.LineString) mls.getGeometryN(i));
        }
        return new MultiLineString(lines.stream().map(GeometryUtils::toLineString).collect(Collectors.toList()));
    }

    private static LineString toLineString(com.vividsolutions.jts.geom.LineString l) {
        final List<com.vividsolutions.jts.geom.Point> points = new ArrayList<>();
        for (int i = 0; i < l.getNumPoints(); i++) {
            points.add(l.getPointN(i));
        }
        return new LineString(points.stream().map(GeometryUtils::toPoint).collect(Collectors.toList()));
    }

    private static Point toPoint(com.vividsolutions.jts.geom.Point p) {
        return new Point(p.getX(), p.getY());
    }

}
