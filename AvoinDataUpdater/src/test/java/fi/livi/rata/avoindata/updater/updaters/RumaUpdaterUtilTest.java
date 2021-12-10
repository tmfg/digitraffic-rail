package fi.livi.rata.avoindata.updater.updaters;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class RumaUpdaterUtilTest extends BaseTest {

    private static final GeometryFactory gf = new GeometryFactory();

    @Test(expected = IllegalArgumentException.class)
    public void getPointFromGeometryCollectionThrowsIfMissingPoint() {
        final GeometryCollection coll = gf.createGeometryCollection(new Geometry[]{});

        RumaUpdaterUtil.getPointFromGeometryCollection(coll, "test");
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
