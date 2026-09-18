package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.Clock;
import java.time.Duration;

/**
 * Everything one GTFS run carries through the pipeline: what happened (metrics) and what work is
 * still permitted (policy). Passed as a single parameter so the two concerns stay separable without
 * adding an argument per layer.
 */
public class GtfsRunContext {
    private static final String NO_FEED = "unknown";

    private final GtfsRunMetrics metrics;
    private final RouteWorkPolicy routePolicy;
    private String currentFeed = NO_FEED;

    public GtfsRunContext(final Duration feedBudget, final Clock clock) {
        this.metrics = new GtfsRunMetrics(clock);
        this.routePolicy = new RouteWorkPolicy(feedBudget, clock);
    }

    public GtfsRunMetrics metrics() {
        return metrics;
    }

    public RouteWorkPolicy routePolicy() {
        return routePolicy;
    }

    public String currentFeed() {
        return currentFeed;
    }

    public void startFeed(final String feedName) {
        currentFeed = feedName;
        routePolicy.startFeed();
        metrics.recordFeedAttempt(feedName);
    }
}
