package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.Locale;

/**
 * Why a segment has no real geometry and fell back to a straight line. 
 */
public enum NoGeometryReason {
    NO_START_NODE,
    NO_END_NODE,
    ROUTE_HTTP_ERROR,
    ROUTE_EMPTY_GEOMETRY,
    NO_DIJKSTRA_PATH,
    ROUTE_PROCESSING_ERROR,
    PREVIOUSLY_FAILED;

    private final String attribute = name().toLowerCase(Locale.ROOT);

    public String attribute() {
        return attribute;
    }
}
