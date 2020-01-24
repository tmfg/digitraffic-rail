package fi.livi.rata.avoindata.updater.service;

import com.vividsolutions.jts.geom.*;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;
import org.osgeo.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import static fi.livi.rata.avoindata.updater.CoordinateTestData.*;

import static org.junit.Assert.assertEquals;

public class Wgs84ConversionServiceTest extends BaseTest {

    @Autowired
    private Wgs84ConversionService service;

    private final GeometryFactory gf = new GeometryFactory();

    private final double ALLOWED_DELTA = 0.1;

    @Test
    public void wgs84ToLivi() {
        final ProjCoordinate c = service.wgs84Tolivi(TAMPERE_WGS84_X, TAMPERE_WGS84_Y);
        assertEquals(TAMPERE_TM35FIN_X, c.x, ALLOWED_DELTA);
        assertEquals(TAMPERE_TM35FIN_Y, c.y, ALLOWED_DELTA);
    }

    @Test
    public void liviToWgs84() {
        final ProjCoordinate c = service.liviToWgs84(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y);
        assertEquals(TAMPERE_WGS84_X, c.x, ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, c.y, ALLOWED_DELTA);
    }

    @Test
    public void liviToWgs84_point() {
        final Point p = (Point) service.liviToWgs84Jts(gf.createPoint(new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y)));
        assertEquals(TAMPERE_WGS84_X, p.getX(), ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, p.getY(), ALLOWED_DELTA);
    }

    @Test
    public void liviToWgs84_lineString() {
        final LineString ls = (LineString) service.liviToWgs84Jts(createLineString());
        checkLineString(ls);
    }

    @Test
    public void liviToWgs84_multiLineString() {
        final MultiLineString mls = (MultiLineString) service.liviToWgs84Jts(gf.createMultiLineString(new LineString[]{createLineString()}));
        checkLineString((LineString) mls.getGeometryN(0));
    }

    @Test
    public void liviToWgs84_polygon() {
        final Polygon p = (Polygon) service.liviToWgs84Jts(gf.createPolygon(gf.createLinearRing(new Coordinate[]{
                new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y),
                new Coordinate(VUOSAARI_TM35FIN_X, VUOSAARI_TM35FIN_Y),
                new Coordinate(NAANTALI_TM35FIN_X, NAANTALI_TM35FIN_Y),
                new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y)
        })));
        final LineString ls = p.getExteriorRing();
        assertEquals(TAMPERE_WGS84_X, ls.getCoordinates()[0].x, ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, ls.getCoordinates()[0].y, ALLOWED_DELTA);
        assertEquals(VUOSAARI_WGS84_X, ls.getCoordinates()[1].x, ALLOWED_DELTA);
        assertEquals(VUOSAARI_WGS84_Y, ls.getCoordinates()[1].y, ALLOWED_DELTA);
        assertEquals(NAANTALI_WGS84_X, ls.getCoordinates()[2].x, ALLOWED_DELTA);
        assertEquals(NAANTALI_WGS84_Y, ls.getCoordinates()[2].y, ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_X, ls.getCoordinates()[3].x, ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, ls.getCoordinates()[3].y, ALLOWED_DELTA);
    }

    @Test
    public void liviToWgs84_geometryCollection() {
        final Point p = gf.createPoint(new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y));
        final Geometry gc = service.liviToWgs84Jts(gf.createGeometryCollection(new Geometry[]{p}));
        Point reprojectedPoint = (Point) gc.getGeometryN(0);
        assertEquals(TAMPERE_WGS84_X, reprojectedPoint.getX(), ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, reprojectedPoint.getY(), ALLOWED_DELTA);
    }

    private void checkLineString(LineString ls) {
        assertEquals(TAMPERE_WGS84_X, ls.getCoordinates()[0].x, ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, ls.getCoordinates()[0].y, ALLOWED_DELTA);
        assertEquals(VUOSAARI_WGS84_X, ls.getCoordinates()[1].x, ALLOWED_DELTA);
        assertEquals(VUOSAARI_WGS84_Y, ls.getCoordinates()[1].y, ALLOWED_DELTA);
    }

    private LineString createLineString() {
        return gf.createLineString(new Coordinate[]{
                new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y),
                new Coordinate(VUOSAARI_TM35FIN_X, VUOSAARI_TM35FIN_Y)
        });
    }

}
