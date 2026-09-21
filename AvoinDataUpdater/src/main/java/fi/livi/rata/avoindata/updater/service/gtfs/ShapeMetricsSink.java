package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.Map;
import java.util.Optional;

/**
 * What {@link GTFSShapeService} records while building one feed's geometry. Feed lifecycle and run
 * outcome are deliberately absent: this layer processes shapes, it does not decide how the run ends.
 */
public interface ShapeMetricsSink {

    void recordRouteLookup();

    void recordRealSegment();

    /**
     * Segment totals only. The reason is recorded separately by whichever layer knew it, so that a
     * segment resolved through {@link RouteMetricsSink} is not attributed twice.
     */
    void recordDummySegment(String stationCode);

    void recordNoGeometryReason(NoGeometryReason reason);

    Optional<Map<String, Object>> recordRouteFailure(String segment, String urlPath, int statusCode, String errorType);

    /** @return the heartbeat event when this shape lands on a heartbeat boundary. */
    Optional<Map<String, Object>> recordShapeProcessed(int processedShapes, int totalShapesInFeed);

    void recordShapes(int total, int real);

    void recordFeedDegraded();
}
