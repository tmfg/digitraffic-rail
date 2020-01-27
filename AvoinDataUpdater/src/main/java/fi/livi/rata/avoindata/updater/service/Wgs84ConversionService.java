package fi.livi.rata.avoindata.updater.service;

import com.vividsolutions.jts.geom.*;
import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;
import org.osgeo.proj4j.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class Wgs84ConversionService {
    public static final int NUMBER_OF_DECIMALS = 6;
    private CoordinateTransform transformer;
    private CoordinateTransform reverseTransformer;
    private GeometryFactory geometryFactory;

    @PostConstruct
    private void setup() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem coordinateTransformFrom = crsFactory.createFromParameters("EPSG:3067",
                "+proj=utm +zone=35 ellps=GRS80 +units=m +no_defs");
        CoordinateReferenceSystem coordinateTransformTo = crsFactory.createFromParameters("EPSG:4326",
                "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs");
        CoordinateTransformFactory coordinateTransformFactory = new CoordinateTransformFactory();
        transformer = coordinateTransformFactory.createTransform(coordinateTransformFrom, coordinateTransformTo);
        reverseTransformer = coordinateTransformFactory.createTransform(coordinateTransformTo, coordinateTransformFrom);
        geometryFactory = new GeometryFactory();
    }

    public ProjCoordinate liviToWgs84(double iKoordinaatti, double pKoordinaatti) {
        ProjCoordinate from = new ProjCoordinate();
        ProjCoordinate to = new ProjCoordinate();
        from.x = iKoordinaatti;
        from.y = pKoordinaatti;

        transformer.transform(from, to);
        to.setValue(round(to.x,NUMBER_OF_DECIMALS),round(to.y, NUMBER_OF_DECIMALS));
        return to;
    }

    public Geometry liviToWgs84Jts(Geometry tm35FinGeometry) {
        Geometry reprojectedGeometry = null;
        switch (tm35FinGeometry.getGeometryType()) {
            case "Point":
                reprojectedGeometry = transformJtsPoint((Point) tm35FinGeometry);
                break;
            case "LineString":
                reprojectedGeometry = transformJtsLineString((LineString) tm35FinGeometry);
                break;
            case "MultiLineString":
                reprojectedGeometry = transformJtsMultiLineString((MultiLineString) tm35FinGeometry);
                break;
            case "Polygon":
                reprojectedGeometry = transformJtsPolygon((Polygon) tm35FinGeometry);
                break;
            case "GeometryCollection":
                reprojectedGeometry = transformJtsGeometryCollection((GeometryCollection) tm35FinGeometry);
                break;
        }
        if (reprojectedGeometry == null) {
            throw new IllegalArgumentException("Unknown geometry type: " + tm35FinGeometry.getGeometryType());
        }
        reprojectedGeometry.setSRID(SpatialConstants.WGS84_SRID);
        return reprojectedGeometry;
    }

    private Geometry transformJtsGeometryCollection(GeometryCollection tm35FinGeometry) {
        List<Geometry> geoms = new ArrayList<>();
        for (int i = 0; i < tm35FinGeometry.getNumGeometries(); i++) {
            geoms.add(liviToWgs84Jts(tm35FinGeometry.getGeometryN(i)));
        }
        return new GeometryCollection(geoms.toArray(Geometry[]::new), geometryFactory);
    }

    // only exterior ring supported, no holes
    private Geometry transformJtsPolygon(Polygon tm35FinGeometry) {
        return geometryFactory.createPolygon(geometryFactory.createLinearRing(Arrays.stream(tm35FinGeometry.getExteriorRing().getCoordinates()).map(this::transformJtsCoordinate).toArray(Coordinate[]::new)));
    }

    private Geometry transformJtsMultiLineString(MultiLineString tm35FinGeometry) {
        final List<LineString> lines = new ArrayList<>();
        for(int i = 0; i != tm35FinGeometry.getNumGeometries(); ++i) {
            lines.add((com.vividsolutions.jts.geom.LineString) tm35FinGeometry.getGeometryN(i));
        }
        return geometryFactory.createMultiLineString(lines.stream().map(this::transformJtsLineString).toArray(LineString[]::new));
    }

    private Point transformJtsPoint(Point tm35FinGeometry) {
        return geometryFactory.createPoint(transformJtsCoordinate(tm35FinGeometry.getCoordinate()));
    }

    private LineString transformJtsLineString(LineString tm35FinGeometry) {
        return geometryFactory.createLineString(Arrays.stream(tm35FinGeometry.getCoordinates()).map(this::transformJtsCoordinate).toArray(Coordinate[]::new));
    }

    private Coordinate transformJtsCoordinate(Coordinate liviCoordinate) {
        ProjCoordinate reprojected = liviToWgs84(liviCoordinate.x, liviCoordinate.y);
        return new Coordinate(reprojected.x, reprojected.y);
    }

    public ProjCoordinate wgs84Tolivi(double x, double y) {
        ProjCoordinate from = new ProjCoordinate();
        ProjCoordinate to = new ProjCoordinate();
        from.x = x;
        from.y = y;

        reverseTransformer.transform(from, to);
        return to;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
