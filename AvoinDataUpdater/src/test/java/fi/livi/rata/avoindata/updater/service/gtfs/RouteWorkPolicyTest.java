package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteWorkPolicyTest {
    private static final Duration FEED_BUDGET = Duration.ofMinutes(10);
    private static final Instant START = Instant.parse("2025-09-17T03:00:00Z");

    /** Lets the budget be exercised without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now = START;

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        private void advance(final Duration amount) {
            now = now.plus(amount);
        }
    }

    @Test
    void givenUntouchedSegmentWhenDecidingThenRouteRequestProceeds() {
        // Given
        final RouteWorkPolicy policy = new RouteWorkPolicy(FEED_BUDGET, new MutableClock());

        // When
        final RouteWorkPolicy.Decision decision = policy.decide("AAA->BBB");

        // Then
        assertThat(decision.proceed()).isTrue();
        assertThat(decision.dummyReason()).isNull();
    }

    @Test
    void givenFailedSegmentWhenDecidingAgainThenItIsNegativeCachedForTheRest() {
        // Given
        final RouteWorkPolicy policy = new RouteWorkPolicy(FEED_BUDGET, new MutableClock());
        policy.recordFailure("AAA->BBB");

        // When
        final RouteWorkPolicy.Decision decision = policy.decide("AAA->BBB");

        // Then a failing pair is not retried once per feed
        assertThat(decision.proceed()).isFalse();
        assertThat(decision.dummyReason()).isEqualTo(DummyReason.PREVIOUSLY_FAILED);
    }

    @Test
    void givenFailedSegmentWhenDecidingForAnotherSegmentThenItStillProceeds() {
        // Given
        final RouteWorkPolicy policy = new RouteWorkPolicy(FEED_BUDGET, new MutableClock());
        policy.recordFailure("AAA->BBB");

        // When / Then
        assertThat(policy.decide("CCC->DDD").proceed()).isTrue();
    }

    @Test
    void givenSpentFeedBudgetWhenDecidingThenNoNewRequestStarts() {
        // Given
        final MutableClock clock = new MutableClock();
        final RouteWorkPolicy policy = new RouteWorkPolicy(FEED_BUDGET, clock);

        // When
        clock.advance(FEED_BUDGET);
        final RouteWorkPolicy.Decision decision = policy.decide("AAA->BBB");

        // Then
        assertThat(decision.proceed()).isFalse();
        assertThat(decision.dummyReason()).isEqualTo(DummyReason.FEED_BUDGET_EXHAUSTED);
    }

    @Test
    void givenSpentBudgetWhenTheNextFeedStartsThenTheBudgetIsRearmed() {
        // Given
        final MutableClock clock = new MutableClock();
        final RouteWorkPolicy policy = new RouteWorkPolicy(FEED_BUDGET, clock);
        clock.advance(FEED_BUDGET);
        assertThat(policy.decide("AAA->BBB").proceed()).isFalse();

        // When
        policy.startFeed();

        // Then the budget is per feed, not per run
        assertThat(policy.decide("AAA->BBB").proceed()).isTrue();
    }

    @Test
    void givenFailedSegmentWhenTheNextFeedStartsThenItIsStillNotRetried() {
        // Given
        final RouteWorkPolicy policy = new RouteWorkPolicy(FEED_BUDGET, new MutableClock());
        policy.recordFailure("AAA->BBB");

        // When
        policy.startFeed();

        // Then the negative cache spans the run, so five feeds do not multiply upstream load
        assertThat(policy.decide("AAA->BBB").dummyReason()).isEqualTo(DummyReason.PREVIOUSLY_FAILED);
    }
}
