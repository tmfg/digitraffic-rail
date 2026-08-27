package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.metadata.Station;

class SiriInMemoryStationUicLookupTest {

    // --- DBUL-01: Known shortCode returns its uicCode ---

    @Test
    void givenKnownStation_whenUicFor_thenReturnsUicCode() {
        // given
        final InMemoryStationUicLookup lookup = new InMemoryStationUicLookup(List.of(createStation("HKI", 1)));

        // when
        final OptionalInt result = lookup.uicFor("HKI");

        // then
        assertEquals(OptionalInt.of(1), result);
    }

    // --- DBUL-02: Unknown shortCode returns empty ---

    @Test
    void givenUnknownStation_whenUicFor_thenReturnsEmpty() {
        // given
        final InMemoryStationUicLookup lookup = new InMemoryStationUicLookup(List.of(createStation("HKI", 1)));

        // when
        final OptionalInt result = lookup.uicFor("XYZ");

        // then
        assertEquals(OptionalInt.empty(), result);
    }

    // --- DBUL-03: Multiple stations indexed correctly ---

    @Test
    void givenMultipleStations_whenUicFor_thenReturnsCorrectUic() {
        // given
        final InMemoryStationUicLookup lookup = new InMemoryStationUicLookup(List.of(
                createStation("HKI", 1),
                createStation("TPE", 160),
                createStation("TKU", 130)
        ));

        // when
        final OptionalInt result = lookup.uicFor("TPE");

        // then
        assertEquals(OptionalInt.of(160), result);
    }

    // --- DBUL-04: Duplicate shortCode keeps first entry ---

    @Test
    void givenDuplicateShortCode_whenUicFor_thenFirstWins() {
        // given
        final InMemoryStationUicLookup lookup = new InMemoryStationUicLookup(List.of(
                createStation("HKI", 1),
                createStation("HKI", 99)
        ));

        // when
        final OptionalInt result = lookup.uicFor("HKI");

        // then
        assertEquals(OptionalInt.of(1), result);
    }

    // --- DBUL-05: Empty station list → all lookups empty ---

    @Test
    void givenEmptyStationList_whenUicFor_thenReturnsEmpty() {
        // given
        final InMemoryStationUicLookup lookup = new InMemoryStationUicLookup(List.of());

        // when
        final OptionalInt result = lookup.uicFor("HKI");

        // then
        assertEquals(OptionalInt.empty(), result);
    }

    // ===== HELPERS =====

    private static Station createStation(final String shortCode, final int uicCode) {
        final Station station = new Station();
        station.shortCode = shortCode;
        station.uicCode = uicCode;
        return station;
    }
}
