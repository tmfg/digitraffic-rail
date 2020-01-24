package fi.livi.rata.avoindata.updater.service;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;
import org.osgeo.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;

public class Wgs84ConversionServiceTest extends BaseTest {

    @Autowired
    private Wgs84ConversionService service;

    private final GeometryFactory gf = new GeometryFactory();

    private final double TAMPERE_WGS84_X = 23.774862;
    private final double TAMPERE_WGS84_Y = 61.486365;

    private final double TAMPERE_TM35FIN_X = 328288.5;
    private final double TAMPERE_TM35FIN_Y = 6821211;
    
    private final double ALLOWED_DELTA = 0.1;

    @Test
    public void wgs84ToLivi() {
        ProjCoordinate c = service.wgs84Tolivi(TAMPERE_WGS84_X, TAMPERE_WGS84_Y);
        assertEquals(TAMPERE_TM35FIN_X, c.x, ALLOWED_DELTA);
        assertEquals(TAMPERE_TM35FIN_Y, c.y, ALLOWED_DELTA);
    }

    @Test
    public void liviToWgs84() {
        ProjCoordinate c = service.liviToWgs84(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y);
        assertEquals(TAMPERE_WGS84_X, c.x, ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, c.y, ALLOWED_DELTA);
    }

    @Test
    public void liviToWgs84_geometry() {
        Point p = (Point) service.liviToWgs84Jts(gf.createPoint(new Coordinate(TAMPERE_TM35FIN_X, TAMPERE_TM35FIN_Y)));
        assertEquals(TAMPERE_WGS84_X, p.getX(), ALLOWED_DELTA);
        assertEquals(TAMPERE_WGS84_Y, p.getY(), ALLOWED_DELTA);
    }

}
