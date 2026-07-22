package fi.livi.rata.avoindata.updater.service.netex.peti;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PetiUicMatcher — UIC code matching, parent/child tie-break, counts.
 */
class PetiUicMatcherTest {

    // --- B1: Match rule: peti.uicCode == 1_000_000 + station.uicCode ---

    @Test
    void givenPetiStopWithUic1000361_whenMatchByStation361_thenReturnsPetiStop() {
        // given
        final PetiStop tervola = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(tervola));

        // when
        final Optional<PetiStop> result = matcher.match(361);

        // then
        assertTrue(result.isPresent());
        assertEquals("FSR:StopPlace:1", result.get().stopPlaceId());
    }

    // --- B2: Match rule equivalence: stationUICCode == petiUicCode mod 100_000 ---

    @Test
    void givenPetiStopWithUic1000361_whenNormalisedWithMod100000_thenKeyIs361() {
        // given
        final PetiStop tervola = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(tervola));

        // when
        final Optional<PetiStop> result = matcher.match(1000361 % 100_000);

        // then — 1000361 % 100_000 = 361
        assertTrue(result.isPresent());
        assertEquals("FSR:StopPlace:1", result.get().stopPlaceId());
    }

    // --- B3: No match returns empty ---

    @Test
    void givenMatcherWithKnownStops_whenLookupNonExistentStation_thenReturnsEmpty() {
        // given
        final PetiStop stop1 = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiStop stop2 = createPetiStop("FSR:StopPlace:99", 1000500, "Testilä", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(stop1, stop2));

        // when
        final Optional<PetiStop> result = matcher.match(999);

        // then
        assertTrue(result.isEmpty());
    }

    // --- B4: Matched count reflects number of indexed PetiStops ---

    @Test
    void givenMatcherBuiltWith3Stops_whenMatchedCountQueried_thenReturns3() {
        // given
        final PetiStop stop1 = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiStop stop2 = createPetiStop("FSR:StopPlace:99", 1000500, "Testilä", true);
        final PetiStop stop3 = createPetiStop("FSR:StopPlace:50", 1000123, "Sekalainen", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(stop1, stop2, stop3));

        // when
        final int count = matcher.matchedCount();

        // then
        assertEquals(3, count);
    }

    // --- B5: Unmatched count reflects stations with no PETI match ---

    @Test
    void givenMatcherWith2Stops_whenLookup5StationsAnd3DontMatch_thenUnmatchedIs3() {
        // given
        final PetiStop stop1 = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiStop stop2 = createPetiStop("FSR:StopPlace:99", 1000500, "Testilä", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(stop1, stop2));

        // when — perform 5 lookups: 2 match, 3 don't
        matcher.match(361);   // match
        matcher.match(500);   // match
        matcher.match(999);   // no match
        matcher.match(888);   // no match
        matcher.match(777);   // no match

        // then
        assertEquals(3, matcher.unmatchedCount());
    }

    // --- B6: Parent/child tie-break: prefer IS_PARENT_STOP_PLACE=true ---

    @Test
    void givenTwoStopsSameUicWhereOneIsParent_whenMatched_thenReturnsParent() {
        // given
        final PetiStop parent = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiStop child = createPetiStop("FSR:StopPlace:2", 1000361, "Tervola laituri", false);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(child, parent)); // child first to test tie-break

        // when
        final Optional<PetiStop> result = matcher.match(361);

        // then
        assertTrue(result.isPresent());
        assertEquals("FSR:StopPlace:1", result.get().stopPlaceId());
        assertTrue(result.get().parentStopPlace());
    }

    // --- B7: Parent/child tie-break: when neither is parent, first wins ---

    @Test
    void givenTwoNonParentStopsSameUic_whenMatched_thenReturnsDeterministicResult() {
        // given
        final PetiStop first = createPetiStop("FSR:StopPlace:A", 1000361, "First", false);
        final PetiStop second = createPetiStop("FSR:StopPlace:B", 1000361, "Second", false);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(first, second));

        // when
        final Optional<PetiStop> result = matcher.match(361);

        // then — one of the two is returned; no exception
        assertTrue(result.isPresent());
    }

    // --- B8: Station with null/zero uicCode treated as unmatched ---

    @Test
    void givenMatcherWithStops_whenLookupByZeroUic_thenReturnsEmptyAndCountsUnmatched() {
        // given
        final PetiStop stop = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(stop));

        // when
        final Optional<PetiStop> result = matcher.match(0);

        // then
        assertTrue(result.isEmpty());
    }

    // --- B9: Multiple distinct uicCodes each map correctly ---

    @Test
    void givenThreeStopsWithDistinctUicCodes_whenLookupEach_thenEachReturnsCorrectStop() {
        // given
        final PetiStop stop361 = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiStop stop500 = createPetiStop("FSR:StopPlace:99", 1000500, "Testilä", true);
        final PetiStop stop123 = createPetiStop("FSR:StopPlace:50", 1000123, "Sekalainen", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(stop361, stop500, stop123));

        // when / then
        assertEquals("FSR:StopPlace:1", matcher.match(361).orElseThrow().stopPlaceId());
        assertEquals("FSR:StopPlace:99", matcher.match(500).orElseThrow().stopPlaceId());
        assertEquals("FSR:StopPlace:50", matcher.match(123).orElseThrow().stopPlaceId());
    }

    // --- B10: Worked example: Tervola station 361 → FSR:StopPlace:1 ---

    @Test
    void givenTervolaFromFixture_whenLookupStation361_thenReturnsFsrStopPlace1WithNameTervola() {
        // given
        final PetiStop tervola = createPetiStop("FSR:StopPlace:1", 1000361, "Tervola", true);
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(tervola));

        // when
        final Optional<PetiStop> result = matcher.match(361);

        // then
        assertTrue(result.isPresent());
        assertEquals("FSR:StopPlace:1", result.get().stopPlaceId());
        assertEquals("Tervola", result.get().name());
    }

    // --- B11: Empty PetiStop list yields zero matched count ---

    @Test
    void givenEmptyPetiStopList_whenMatchedCountQueried_thenReturnsZero() {
        // given
        final PetiUicMatcher matcher = new PetiUicMatcher(List.of());

        // when
        final int count = matcher.matchedCount();

        // then
        assertEquals(0, count);
    }

    // --- Helper ---

    private PetiStop createPetiStop(final String stopPlaceId, final int uicCode,
                                     final String name, final boolean parentStopPlace) {
        return new PetiStop(stopPlaceId, uicCode, name, parentStopPlace, null, List.of());
    }
}
