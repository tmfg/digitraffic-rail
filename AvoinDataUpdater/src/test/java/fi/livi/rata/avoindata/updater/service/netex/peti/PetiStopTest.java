package fi.livi.rata.avoindata.updater.service.netex.peti;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PetiStop quay resolution logic.
 */
class PetiStopTest {

    // --- C1: Quay found by publicCode matching commercialTrack ---

    @Test
    void givenStopWithTwoQuays_whenResolveQuayByTrack1_thenReturnsQuay7() {
        // given
        final PetiQuay quay7 = new PetiQuay("FSR:Quay:7", "1", null);
        final PetiQuay quay10 = new PetiQuay("FSR:Quay:10", "2", null);
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
        final PetiQuay quay7 = new PetiQuay("FSR:Quay:7", "1", null);
        final PetiQuay quay10 = new PetiQuay("FSR:Quay:10", "2", null);
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
        final PetiQuay quay7 = new PetiQuay("FSR:Quay:7", "1", null);
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
        final PetiQuay quay7 = new PetiQuay("FSR:Quay:7", "1", null);
        final PetiQuay quay10 = new PetiQuay("FSR:Quay:10", "2", null);
        final PetiStop tervola = new PetiStop("FSR:StopPlace:1", 1000361, "Tervola", true, null,
                List.of(quay7, quay10));

        // when
        final Optional<PetiQuay> result = tervola.resolveQuay("2");

        // then
        assertTrue(result.isPresent());
        assertEquals("FSR:Quay:10", result.get().quayId());
        assertEquals("2", result.get().publicCode());
    }
}
