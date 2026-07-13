package fi.livi.rata.avoindata.updater.service.recentlyseen;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;

/**
 * Pins the current behaviour of {@link RecentlySeenTrainLocationFilter}: it deduplicates by
 * {trainNumber}_{departureDate}_{timestamp} key and prunes its in-memory map after
 * {@link RecentlySeenTrainLocationFilter#TIMESTAMP_RECENT_TRESHOLD_MINUTES} minutes (allowing re-emission).
 * It does NOT reject stale entries.
 *
 * The filter is a stateful singleton bean, so each test method uses a distinct train number to avoid cross-test
 * pollution.
 */
public class RecentlySeenTrainLocationFilterTest extends BaseTest {
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 1, 1);

    @Autowired
    private RecentlySeenTrainLocationFilter recentlySeenTrainLocationFilter;

    private static TrainLocation location(final long trainNumber, final ZonedDateTime timestamp) {
        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.trainLocationId = new TrainLocationId(trainNumber, DEPARTURE_DATE, timestamp);
        return trainLocation;
    }

    @Test
    public void shouldDeduplicateByKeyWithinBatch() {
        final ZonedDateTime timestamp = DateProvider.nowInHelsinki();
        final List<TrainLocation> result = recentlySeenTrainLocationFilter.filter(
                List.of(location(101L, timestamp), location(101L, timestamp)));

        Assertions.assertEquals(1, result.size());
    }

    @Test
    public void shouldSuppressAlreadySeenKeyOnSecondCall() {
        final ZonedDateTime timestamp = DateProvider.nowInHelsinki();

        final List<TrainLocation> first = recentlySeenTrainLocationFilter.filter(List.of(location(102L, timestamp)));
        Assertions.assertEquals(1, first.size());

        // Same fresh key is still "recently seen" -> suppressed. The filter suppresses by key recency, not entry age.
        final List<TrainLocation> second = recentlySeenTrainLocationFilter.filter(List.of(location(102L, timestamp)));
        Assertions.assertEquals(0, second.size());
    }

    @Test
    public void shouldAcceptFreshDistinctKey() {
        final List<TrainLocation> result = recentlySeenTrainLocationFilter.filter(
                List.of(location(103L, DateProvider.nowInHelsinki())));

        Assertions.assertEquals(1, result.size());
    }

    @Test
    public void shouldReEmitKeyAfterItIsPruned() {
        // A >25-minute-old, unseen key is emitted (age does not reject), then its map entry is pruned...
        final ZonedDateTime oldTimestamp = DateProvider.nowInHelsinki().minusMinutes(30);

        final List<TrainLocation> first = recentlySeenTrainLocationFilter.filter(List.of(location(104L, oldTimestamp)));
        Assertions.assertEquals(1, first.size());

        // ...so the same entry is emitted again on the next call (re-emission the migration must preserve).
        final List<TrainLocation> second = recentlySeenTrainLocationFilter.filter(List.of(location(104L, oldTimestamp)));
        Assertions.assertEquals(1, second.size());
    }

    // --- Test 12: PALA timestamps (no milliseconds) ---

    @Test
    public void shouldAcceptPalaTimestampFormat() {
        // given — PALA timestamp without milliseconds (e.g., "2026-07-06T08:34:29Z")
        final ZonedDateTime palaTimestamp = ZonedDateTime.parse("2026-07-06T08:34:29Z");

        // when
        final List<TrainLocation> result = recentlySeenTrainLocationFilter.filter(
                List.of(location(201L, palaTimestamp)));

        // then — accepted (first time seen)
        Assertions.assertEquals(1, result.size());
    }

    @Test
    public void shouldDeduplicateIdenticalPalaTimestamps() {
        // given — two locations with the same PALA-format timestamp
        final ZonedDateTime palaTimestamp = ZonedDateTime.parse("2026-07-06T08:35:00Z");

        // when
        final List<TrainLocation> result = recentlySeenTrainLocationFilter.filter(
                List.of(location(202L, palaTimestamp), location(202L, palaTimestamp)));

        // then — deduplicated to one
        Assertions.assertEquals(1, result.size());
    }

    @Test
    public void shouldAcceptDifferentPalaTimestamps() {
        // given — two locations with different PALA-format timestamps
        final ZonedDateTime ts1 = ZonedDateTime.parse("2026-07-06T08:36:00Z");
        final ZonedDateTime ts2 = ZonedDateTime.parse("2026-07-06T08:36:01Z");

        // when
        final List<TrainLocation> result = recentlySeenTrainLocationFilter.filter(
                List.of(location(203L, ts1), location(203L, ts2)));

        // then — both accepted (different keys)
        Assertions.assertEquals(2, result.size());
    }
}
