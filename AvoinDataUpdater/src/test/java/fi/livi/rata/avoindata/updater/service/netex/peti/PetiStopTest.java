package fi.livi.rata.avoindata.updater.service.netex.peti;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests for PetiStop quay resolution logic.
 */
class PetiStopTest {

    // --- C1: Quay found by publicCode matching commercialTrack ---

    @Test
    void givenStopWithTwoQuays_whenResolveQuayByTrack1_thenReturnsQuay7() {
        // given
        final PetiQuay quay7 = quay("FSR:Quay:7", "1");
        final PetiQuay quay10 = quay("FSR:Quay:10", "2");
        final PetiStop stop = new PetiStop("FSR:StopPlace:1", 1000361, "Tervola", true, null,
                List.of(quay7, quay10));

        // when
        final Optional<PetiQuay> result = stop.resolveQuay("1");

        // then
        assertTrue(result.isPresent());
        assertEquals("FSR:Quay:7", result.get().quayId());
    }

    // --- C2: Quay not found when commercialTrack doesn't match any publicCode ---

    @Test
    void givenStopWithQuays1And2_whenResolveQuayByTrack3_thenReturnsEmpty() {
        // given
        final PetiQuay quay7 = quay("FSR:Quay:7", "1");
        final PetiQuay quay10 = quay("FSR:Quay:10", "2");
        final PetiStop stop = new PetiStop("FSR:StopPlace:1", 1000361, "Tervola", true, null,
                List.of(quay7, quay10));

        // when
        final Optional<PetiQuay> result = stop.resolveQuay("3");

        // then
        assertTrue(result.isEmpty());
    }

    // --- C3: Quay resolution with null commercialTrack returns empty ---

    @Test
    void givenStopWithQuays_whenResolveQuayWithNull_thenReturnsEmpty() {
        // given
        final PetiQuay quay7 = quay("FSR:Quay:7", "1");
        final PetiStop stop = new PetiStop("FSR:StopPlace:1", 1000361, "Tervola", true, null,
                List.of(quay7));

        // when
        final Optional<PetiQuay> result = stop.resolveQuay(null);

        // then
        assertTrue(result.isEmpty());
    }

    // --- C4: Quay resolution on stop with no quays returns empty ---

    @Test
    void givenStopWithNoQuays_whenResolveQuayByTrack1_thenReturnsEmpty() {
        // given
        final PetiStop stop = new PetiStop("FSR:StopPlace:99", 1000500, "Testilä", true, null,
                List.of());

        // when
        final Optional<PetiQuay> result = stop.resolveQuay("1");

        // then
        assertTrue(result.isEmpty());
    }

    // --- C5: Worked example: Tervola track "2" → FSR:Quay:10 ---

    @Test
    void givenTervolaStop_whenResolveQuayByTrack2_thenReturnsQuay10() {
        // given
        final PetiQuay quay7 = quay("FSR:Quay:7", "1");
        final PetiQuay quay10 = quay("FSR:Quay:10", "2");
        final PetiStop tervola = new PetiStop("FSR:StopPlace:1", 1000361, "Tervola", true, null,
                List.of(quay7, quay10));

        // when
        final Optional<PetiQuay> result = tervola.resolveQuay("2");

        // then
        assertTrue(result.isPresent());
        assertEquals("FSR:Quay:10", result.get().quayId());
        assertEquals("2", result.get().publicCode());
    }

    // --- firstPlatformCode: lowest-numbered platform, the last-resort track ---

    @Test
    void givenQuaysOutOfOrder_whenFirstPlatformCode_thenReturnsLowestNumber() {
        // quays listed high-to-low to prove selection is by number, not list order
        final PetiStop stop = new PetiStop("FSR:StopPlace:8", 1000001, "Helsinki", true, null,
                List.of(quay("FSR:Quay:82", "2"), quay("FSR:Quay:81", "1")));

        assertEquals(Optional.of("1"), stop.firstPlatformCode());
    }

    @Test
    void givenNumericCodes_whenFirstPlatformCode_thenSortsByValueNotLexically() {
        // "2" must beat "10": lexical order would wrongly pick "10"
        final PetiStop stop = new PetiStop("FSR:StopPlace:9", 1000002, "Example", true, null,
                List.of(quay("FSR:Quay:1", "10"), quay("FSR:Quay:2", "2")));

        assertEquals(Optional.of("2"), stop.firstPlatformCode());
    }

    @Test
    void givenNoPlatformOne_whenFirstPlatformCode_thenReturnsLowestPresent() {
        // a Helsinki commuter station publishing only tracks 3 and 4
        final PetiStop stop = new PetiStop("FSR:StopPlace:17", 1000017, "Malmi", true, null,
                List.of(quay("FSR:Quay:3", "4"), quay("FSR:Quay:4", "3")));

        assertEquals(Optional.of("3"), stop.firstPlatformCode());
    }

    @Test
    void givenOnlyNonNumericCode_whenFirstPlatformCode_thenReturnsThatCode() {
        final PetiStop stop = new PetiStop("FSR:StopPlace:73", 1000073, "Haaparanta", true, null,
                List.of(quay("FSR:Quay:1", "pohjoinen")));

        assertEquals(Optional.of("pohjoinen"), stop.firstPlatformCode());
    }

    @Test
    void givenNoQuays_whenFirstPlatformCode_thenReturnsEmpty() {
        final PetiStop stop = new PetiStop("FSR:StopPlace:99", 1000500, "Testilä", true, null,
                List.of());

        assertTrue(stop.firstPlatformCode().isEmpty());
    }

    /** Quay resolution is by publicCode alone, so these cases need no geography. */
    private static PetiQuay quay(final String quayId, final String publicCode) {
        return new PetiQuay(quayId, publicCode, null, null, null);
    }
}
