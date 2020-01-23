package fi.livi.rata.avoindata.updater.service;

import com.vividsolutions.jts.geom.*;
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

    public Geometry liviToWgs84Jts(Geometry liviGeom) {
        switch (liviGeom.getGeometryType()) {
            case "Point":
                return transformJtsPoint((Point) liviGeom);
            case "LineString":
                return transformJtsLineString((LineString) liviGeom);
            case "MultiLineString":
                return transformJtsMultiLineString((MultiLineString) liviGeom);
            case "Polygon":
                return transformJtsPolygon((Polygon) liviGeom);
            case "GeometryCollection":
                return transformJtsGeometryCollection((GeometryCollection) liviGeom);
        }
        throw new IllegalArgumentException("Unknown geometry type: " + liviGeom.getGeometryType());
    }

    private Geometry transformJtsGeometryCollection(GeometryCollection liviGeom) {
        List<Geometry> geoms = new ArrayList<>();
        for (int i = 0; i < liviGeom.getNumGeometries(); i++) {
            geoms.add(liviToWgs84Jts(liviGeom.getGeometryN(i)));
        }
        return new GeometryCollection(geoms.toArray(Geometry[]::new), geometryFactory);
    }

    // only exterior ring supported, no holes
    private Geometry transformJtsPolygon(Polygon liviGeom) {
        return geometryFactory.createPolygon(geometryFactory.createLinearRing(Arrays.stream(liviGeom.getExteriorRing().getCoordinates()).map(this::transformJtsCoordinate).toArray(Coordinate[]::new)));
    }

    private Geometry transformJtsMultiLineString(MultiLineString liviGeom) {
        final List<LineString> lines = new ArrayList<>();
        for(int i = 0; i != liviGeom.getNumGeometries(); ++i) {
            lines.add((com.vividsolutions.jts.geom.LineString) liviGeom.getGeometryN(i));
        }
        return geometryFactory.createMultiLineString(lines.stream().map(this::transformJtsLineString).toArray(LineString[]::new));
    }

    private Point transformJtsPoint(Point liviGeom) {
        return geometryFactory.createPoint(transformJtsCoordinate(liviGeom.getCoordinate()));
    }

    private LineString transformJtsLineString(LineString liviGeom) {
        return geometryFactory.createLineString(Arrays.stream(liviGeom.getCoordinates()).map(this::transformJtsCoordinate).toArray(Coordinate[]::new));
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
