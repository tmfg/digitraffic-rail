package fi.livi.rata.avoindata.updater.updaters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;

import fi.livi.rata.avoindata.updater.BaseTest;

public class RumaUpdaterUtilTest extends BaseTest {

    private static final GeometryFactory gf = new GeometryFactory();

    @Test
    public void getPointFromGeometryCollectionThrowsIfMissingPoint() {
        final GeometryCollection coll = gf.createGeometryCollection(new Geometry[]{});

        assertThrows(IllegalArgumentException.class, () -> RumaUpdaterUtil.getPointFromGeometryCollection(coll, "test"));
    }

    @Test
    public void isVaihdeWithVaihde() {
        assertTrue(RumaUpdaterUtil.elementIsVaihde("1.2.246.586.1.24.1"));
    }

    @Test
    public void isVaihdeWithOpastin() {
        assertFalse(RumaUpdaterUtil.elementIsVaihde("1.2.246.586.1.14.1"));
    }
}
