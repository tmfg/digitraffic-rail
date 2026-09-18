package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.Locale;

/**
 * Why a segment fell back to straight-line geometry. Separated by owner and fix: an Infra API
 * outage, an empty successful response and a broken node map are different problems that a single
 * fallback ratio would conflate.
 */
public enum DummyReason {
    NO_START_NODE,
    NO_END_NODE,
    ROUTE_HTTP_ERROR,
    /** HTTP 200 carrying an empty {@code geometria}; invisible to any transport metric. */
    ROUTE_EMPTY_GEOMETRY,
    NO_DIJKSTRA_PATH,
    ROUTE_PROCESSING_ERROR,
    PREVIOUSLY_FAILED,
    FEED_BUDGET_EXHAUSTED;

    private final String attribute = name().toLowerCase(Locale.ROOT);

    public String attribute() {
        return attribute;
    }
}
