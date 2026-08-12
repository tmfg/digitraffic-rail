package fi.livi.rata.avoindata.updater.service.siri.common;

import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SiriStopResolverTest {

    private SiriStopResolver resolver;

    @BeforeEach
    void setUp() {
        // given — fixture: one StopPlace with uicCode 1000361 (national=361), two quays
        final PetiQuay quay7 = new PetiQuay("FSR:Quay:7", "1", null);
        final PetiQuay quay8 = new PetiQuay("FSR:Quay:8", "2", null);
        final PetiStop stop = new PetiStop("FSR:StopPlace:1", 1000361, "Tervola", true, null,
                List.of(quay7, quay8));

        final PetiStopSource source = () -> List.of(stop);
        resolver = new SiriStopResolver(source);
    }

    // --- STOP-01: Known station + known track → Quay id ---

    @Test
    void givenKnownStationAndTrack1_whenResolveQuayId_thenReturnsQuay7() {
        // when
        final Optional<String> result = resolver.resolveQuayId(361, "1");

        // then
        assertEquals(Optional.of("FSR:Quay:7"), result);
    }

    // --- STOP-02: Known station + different track → different Quay ---

    @Test
    void givenKnownStationAndTrack2_whenResolveQuayId_thenReturnsQuay8() {
        // when
        final Optional<String> result = resolver.resolveQuayId(361, "2");

        // then
        assertEquals(Optional.of("FSR:Quay:8"), result);
    }

    // --- STOP-03: Known station + null track → StopPlace fallback ---

    @Test
    void givenKnownStationAndNullTrack_whenResolveQuayId_thenReturnsStopPlace() {
        // when
        final Optional<String> result = resolver.resolveQuayId(361, null);

        // then
        assertEquals(Optional.of("FSR:StopPlace:1"), result);
    }

    // --- STOP-04: Known station + unknown track → StopPlace fallback ---

    @Test
    void givenKnownStationAndUnknownTrack_whenResolveQuayId_thenReturnsStopPlace() {
        // when
        final Optional<String> result = resolver.resolveQuayId(361, "99");

        // then
        assertEquals(Optional.of("FSR:StopPlace:1"), result);
    }

    // --- STOP-05: Unknown station → empty ---

    @Test
    void givenUnknownStation_whenResolveQuayId_thenReturnsEmpty() {
        // when
        final Optional<String> result = resolver.resolveQuayId(999, "1");

        // then
        assertEquals(Optional.empty(), result);
    }

    // --- STOP-06: resolveStopPlaceId returns StopPlace regardless of quays ---

    @Test
    void givenKnownStation_whenResolveStopPlaceId_thenReturnsStopPlace() {
        // when
        final Optional<String> result = resolver.resolveStopPlaceId(361);

        // then
        assertEquals(Optional.of("FSR:StopPlace:1"), result);
    }

    // --- STOP-07: resolveStopPlaceId with unknown station → empty ---

    @Test
    void givenUnknownStation_whenResolveStopPlaceId_thenReturnsEmpty() {
        // when
        final Optional<String> result = resolver.resolveStopPlaceId(999);

        // then
        assertEquals(Optional.empty(), result);
    }
}
