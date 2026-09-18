package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a route request may still be issued. Route work is bounded two ways: a segment
 * that already failed this run is not retried for the remaining feeds, and no new request starts
 * once a feed has spent its time budget.
 */
public class RouteWorkPolicy {

    public enum Decision {
        PROCEED(null),
        SKIP_PREVIOUSLY_FAILED(DummyReason.PREVIOUSLY_FAILED),
        SKIP_BUDGET_EXHAUSTED(DummyReason.FEED_BUDGET_EXHAUSTED);

        private final DummyReason dummyReason;

        Decision(final DummyReason dummyReason) {
            this.dummyReason = dummyReason;
        }

        public DummyReason dummyReason() {
            return dummyReason;
        }

        public boolean proceed() {
            return this == PROCEED;
        }
    }

    private final Duration feedBudget;
    private final Clock clock;
    private final Set<String> failedSegments = new HashSet<>();
    private Instant feedStartedAt;

    public RouteWorkPolicy(final Duration feedBudget, final Clock clock) {
        this.feedBudget = feedBudget;
        this.clock = clock;
        this.feedStartedAt = clock.instant();
    }

    public void startFeed() {
        feedStartedAt = clock.instant();
    }

    public Decision decide(final String segment) {
        return decide(segment, false);
    }

    /**
     * @param servedFromCache route already resolved this run; the budget bounds upstream work, so a
     *                        cache hit must not be turned into a dummy segment for no saving.
     */
    public Decision decide(final String segment, final boolean servedFromCache) {
        if (failedSegments.contains(segment)) {
            return Decision.SKIP_PREVIOUSLY_FAILED;
        }
        if (!servedFromCache && Duration.between(feedStartedAt, clock.instant()).compareTo(feedBudget) >= 0) {
            return Decision.SKIP_BUDGET_EXHAUSTED;
        }
        return Decision.PROCEED;
    }

    public void recordFailure(final String segment) {
        failedSegments.add(segment);
    }
}
