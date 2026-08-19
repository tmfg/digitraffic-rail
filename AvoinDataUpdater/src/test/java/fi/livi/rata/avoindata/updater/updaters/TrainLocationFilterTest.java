package fi.livi.rata.avoindata.updater.updaters;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * Tests the extracted {@link TrainLocationUpdater#isIpFallbackLocation(Point)} predicate against the real production
 * logic. The predicate is locale-independent (the formatter is pinned to {@code Locale.ROOT}), so these assertions
 * hold on any JVM.
 */
public class TrainLocationFilterTest {
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    public void ipFallbackLocationShouldBeDetected() {
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
