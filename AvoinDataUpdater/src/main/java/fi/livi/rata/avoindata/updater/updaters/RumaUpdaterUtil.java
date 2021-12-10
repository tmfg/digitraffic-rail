package fi.livi.rata.avoindata.updater.updaters;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.Point;

public class RumaUpdaterUtil {

    private static final String VAIHDE_OID_PREFIX = "1.2.246.586.1.24.";

    static boolean elementIsVaihde(final String elementId) {
        return elementId.startsWith(VAIHDE_OID_PREFIX);
    }

    static Point getPointFromGeometryCollection(
            final GeometryCollection coll,
            final String notificationId) {

        for (int i = 0; i < coll.getNumGeometries(); i++) {
            final Geometry geom = coll.getGeometryN(i);
            if (geom instanceof Point) {
                return (Point) geom;
            }
        }
        throw new IllegalArgumentException(String.format("Geometry collection had no points, notification id %s", notificationId));
    }
}
