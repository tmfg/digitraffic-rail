package fi.livi.rata.avoindata.updater.updaters;

import java.text.DecimalFormat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * Tests the extracted {@link TrainLocationUpdater#isIpFallbackLocation(Point)} predicate against the real production
 * logic.
 */
public class TrainLocationFilterTest {
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    public void ipFallbackLocationShouldBeDetected() {
        // Production's DecimalFormat("#.000000") uses the default JVM locale and compares against comma-separated
        // strings ("60,170800" / "24,937500"), so the positive match only holds under a comma-decimal-separator
        // locale (e.g. fi_FI).
        Assumptions.assumeTrue(new DecimalFormat("#.000000").format(60.170800d).equals("60,170800"),
                "IP filter only matches under a comma-decimal-separator locale");

        final Point helsinkiFallback = geometryFactory.createPoint(new Coordinate(24.937500, 60.170800)); // lon, lat
        Assertions.assertTrue(TrainLocationUpdater.isIpFallbackLocation(helsinkiFallback));
    }

    @Test
    public void onTrackLocationShouldNotBeDetectedAsIpFallback() {
        // A normal on-track WGS84 point is never the Helsinki IP fallback, regardless of locale.
        final Point onTrack = geometryFactory.createPoint(new Coordinate(23.7610, 61.4978));
        Assertions.assertFalse(TrainLocationUpdater.isIpFallbackLocation(onTrack));
    }
}
