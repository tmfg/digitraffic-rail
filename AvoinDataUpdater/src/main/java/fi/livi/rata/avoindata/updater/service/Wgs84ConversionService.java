package fi.livi.rata.avoindata.updater.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;

@Service
public class Wgs84ConversionService {
    public static final int NUMBER_OF_DECIMALS = 6;
    private GeometryFactory geometryFactory;
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private CoordinateTransformFactory coordinateTransformFactory;
    private CoordinateReferenceSystem coordinateTransformTo;
    private CoordinateReferenceSystem coordinateTransformFrom;

    @PostConstruct
    private void setup() {
        final CRSFactory crsFactory = new CRSFactory();
        coordinateTransformFrom = crsFactory.createFromParameters("EPSG:3067",
                "+proj=utm +zone=35 ellps=GRS80 +units=m +no_defs");
        coordinateTransformTo = crsFactory.createFromParameters("EPSG:4326",
                "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs");
        coordinateTransformFactory = new CoordinateTransformFactory();
        geometryFactory = new GeometryFactory();
    }

    public ProjCoordinate liviToWgs84(final double iKoordinaatti, final double pKoordinaatti) {
        final ProjCoordinate from = new ProjCoordinate();
        final ProjCoordinate to = new ProjCoordinate();
        from.x = iKoordinaatti;
        from.y = pKoordinaatti;

        coordinateTransformFactory.createTransform(coordinateTransformFrom, coordinateTransformTo).transform(from, to);
        to.setValue(round(to.x, NUMBER_OF_DECIMALS), round(to.y, NUMBER_OF_DECIMALS));

        return to;
    }

    public Geometry liviToWgs84Jts(final Geometry tm35FinGeometry) {
        final Geometry reprojectedGeometry = switch (tm35FinGeometry.getGeometryType()) {
            case "Point" -> transformJtsPoint((Point) tm35FinGeometry);
            case "LineString" -> transformJtsLineString((LineString) tm35FinGeometry);
            case "MultiLineString" -> transformJtsMultiLineString((MultiLineString) tm35FinGeometry);
            case "Polygon" -> transformJtsPolygon((Polygon) tm35FinGeometry);
            case "GeometryCollection" -> transformJtsGeometryCollection((GeometryCollection) tm35FinGeometry);
            default -> null;
        };
        if (reprojectedGeometry == null) {
            throw new IllegalArgumentException("Unknown geometry type: " + tm35FinGeometry.getGeometryType());
        }
        reprojectedGeometry.setSRID(SpatialConstants.WGS84_SRID);
        return reprojectedGeometry;
    }

    private Geometry transformJtsGeometryCollection(final GeometryCollection tm35FinGeometry) {
        final List<Geometry> geoms = new ArrayList<>();
        for (int i = 0; i < tm35FinGeometry.getNumGeometries(); i++) {
            geoms.add(liviToWgs84Jts(tm35FinGeometry.getGeometryN(i)));
        }
        return new GeometryCollection(geoms.toArray(Geometry[]::new), geometryFactory);
    }

    // only exterior ring supported, no holes
    private Geometry transformJtsPolygon(final Polygon tm35FinGeometry) {
        try {
            return geometryFactory.createPolygon(geometryFactory.createLinearRing(Arrays.stream(tm35FinGeometry.getExteriorRing().getCoordinates()).map(this::transformJtsCoordinate).toArray(Coordinate[]::new)));
        } catch (final Exception e) {
            log.error("Failed trying to create polygon from " + tm35FinGeometry);
            throw e;
        }
    }

    private Geometry transformJtsMultiLineString(final MultiLineString tm35FinGeometry) {
        final List<LineString> lines = new ArrayList<>();
        for (int i = 0; i != tm35FinGeometry.getNumGeometries(); ++i) {
            lines.add((LineString) tm35FinGeometry.getGeometryN(i));
        }
        return geometryFactory.createMultiLineString(lines.stream().map(this::transformJtsLineString).toArray(LineString[]::new));
    }

    private Point transformJtsPoint(final Point tm35FinGeometry) {
        return geometryFactory.createPoint(transformJtsCoordinate(tm35FinGeometry.getCoordinate()));
    }

    private LineString transformJtsLineString(final LineString tm35FinGeometry) {
        return geometryFactory.createLineString(Arrays.stream(tm35FinGeometry.getCoordinates()).map(this::transformJtsCoordinate).toArray(Coordinate[]::new));
    }

    private Coordinate transformJtsCoordinate(final Coordinate liviCoordinate) {
        final ProjCoordinate reprojected = liviToWgs84(liviCoordinate.x, liviCoordinate.y);
        return new Coordinate(reprojected.x, reprojected.y);
    }

    public ProjCoordinate wgs84Tolivi(final double x, final double y) {
        final ProjCoordinate from = new ProjCoordinate();
        final ProjCoordinate to = new ProjCoordinate();
        from.x = x;
        from.y = y;

        coordinateTransformFactory.createTransform(coordinateTransformTo, coordinateTransformFrom).transform(from, to);
        return to;
    }

    public static double round(final double value, final int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
