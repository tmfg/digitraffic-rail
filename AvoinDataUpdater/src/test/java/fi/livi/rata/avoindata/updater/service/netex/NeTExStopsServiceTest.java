package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.netex.peti.EmptyPetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NeTExStopsService — Station to ScheduledStopPoint/RoutePoint/DestinationDisplay mapping
 * and PassengerStopAssignment wiring.
 */
class NeTExStopsServiceTest {

    private NeTExStopsService stopsService;
    private NeTExIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new NeTExIdGenerator();
        stopsService = new NeTExStopsService(idGenerator, new EmptyPetiStopSource());
    }

    @Test
    void givenPassengerStation_whenCreatingStops_thenProducesScheduledStopPointWithCorrectId() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertEquals(1, stopsData.getScheduledStopPoints().size());
        assertEquals("DT:ScheduledStopPoint:HKI", stopsData.getScheduledStopPoints().get(0).id());
    }

    @Test
    void givenStation_whenCreatingStops_thenScheduledStopPointContainsName() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertEquals("Helsinki asema", stopsData.getScheduledStopPoints().get(0).name());
    }

    @Test
    void givenStation_whenCreatingStops_thenScheduledStopPointContainsCoordinates() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertEquals(new BigDecimal("60.172133"), stopsData.getScheduledStopPoints().get(0).latitude());
        assertEquals(new BigDecimal("24.941662"), stopsData.getScheduledStopPoints().get(0).longitude());
    }

    @Test
    void givenStation_whenCreatingStops_thenPrivateCodeIsShortCode() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertEquals("HKI", stopsData.getScheduledStopPoints().get(0).privateCode());
    }

    @Test
    void givenStation_whenCreatingStops_thenProducesRoutePoint() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertEquals(1, stopsData.getRoutePoints().size());
        assertEquals("DT:RoutePoint:HKI", stopsData.getRoutePoints().get(0).id());
    }

    @Test
    void givenStation_whenCreatingStops_thenProducesDestinationDisplay() {
        // given
        final Station station = createStation("TPE", "Tampere", 160, true,
                new BigDecimal("61.498500"), new BigDecimal("23.773000"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertEquals(1, stopsData.getDestinationDisplays().size());
        assertEquals("DT:DestinationDisplay:TPE", stopsData.getDestinationDisplays().get(0).id());
        assertEquals("Tampere", stopsData.getDestinationDisplays().get(0).frontText());
    }

    @Test
    void givenNonPassengerStation_whenCreatingStops_thenExcludedFromOutput() {
        // given
        final Station passengerStation = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));
        final Station nonPassengerStation = createStation("MJJ", "Majajärvi", 1168, false,
                new BigDecimal("61.682946"), new BigDecimal("23.469457"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(passengerStation, nonPassengerStation));

        // then: only the passenger station is included
        assertEquals(1, stopsData.getScheduledStopPoints().size());
        assertEquals("DT:ScheduledStopPoint:HKI", stopsData.getScheduledStopPoints().get(0).id());
    }

    // --- Pass 2: PassengerStopAssignment wiring scenarios ---

    @Test
    void givenEmptyPetiStopSource_whenCreatingStops_thenNoAssignmentsAndZeroCounts() {
        // given — EmptyPetiStopSource (default setUp)
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then
        assertTrue(stopsData.getStopAssignments().isEmpty());
        assertEquals(0, stopsData.matchedCount());
        assertEquals(0, stopsData.unmatchedCount());
    }

    @Test
    void givenMatchingPetiStop_whenCreatingStops_thenAssignmentProduced() {
        // given — station UIC 1 matches PETI stop with UIC 1_000_001
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station));

        // then
        assertEquals(1, stopsData.getStopAssignments().size());
        final var assignment = stopsData.getStopAssignments().get(0);
        assertEquals("DT:ScheduledStopPoint:HKI", assignment.scheduledStopPointRef());
        assertEquals("FSR:StopPlace:1", assignment.stopPlaceRef());
    }

    @Test
    void givenNoMatchingPetiStop_whenCreatingStops_thenNoAssignmentButSspStillProduced() {
        // given — station UIC 1, but PETI has no matching stop
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:99", 1_000_099, "Other", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station));

        // then — no assignment, but SSP is still produced; unmatchedCount == 1
        assertTrue(stopsData.getStopAssignments().isEmpty());
        assertFalse(stopsData.getScheduledStopPoints().isEmpty());
        assertEquals(0, stopsData.matchedCount());
        assertEquals(1, stopsData.unmatchedCount());
    }

    @Test
    void givenMixedMatchAndUnmatch_whenCreatingStops_thenCountsSumCorrectly() {
        // given — two passenger stations: one matched, one unmatched
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station matchedStation = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));
        final Station unmatchedStation = createStation("TPE", "Tampere", 160, true,
                new BigDecimal("61.498500"), new BigDecimal("23.773000"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(matchedStation, unmatchedStation));

        // then
        assertEquals(1, stopsData.getStopAssignments().size());
        assertEquals(1, stopsData.matchedCount());
        assertEquals(1, stopsData.unmatchedCount());
        assertEquals(2, stopsData.matchedCount() + stopsData.unmatchedCount());
    }

    @Test
    void givenNonPassengerStationMatchingPeti_whenCreatingStops_thenNoAssignmentProduced() {
        // given — non-passenger station with matching PETI stop
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1168", 1_001_168, "Majajärvi", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station nonPassengerStation = createStation("MJJ", "Majajärvi", 1168, false,
                new BigDecimal("61.682946"), new BigDecimal("23.469457"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(nonPassengerStation));

        // then — non-passenger stations are excluded entirely
        assertTrue(stopsData.getStopAssignments().isEmpty());
    }

    @Test
    void givenMatchingPetiStop_whenCreatingStops_thenStopPlaceRefIsVerbatimFromPetiStop() {
        // given — verify the stopPlaceRef is the verbatim string from PetiStop, not computed
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:42", 1_000_001, "Helsinki", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station));

        // then — stopPlaceRef is the verbatim string from the matched PetiStop
        assertEquals(1, stopsData.getStopAssignments().size());
        assertEquals("FSR:StopPlace:42", stopsData.getStopAssignments().get(0).stopPlaceRef());
    }

    @Test
    void givenMatchingPetiStop_whenCreatingStops_thenAssignmentIdFollowsPattern() {
        // given
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station));

        // then — assignment ID follows DT:PassengerStopAssignment:{shortCode}
        assertEquals(1, stopsData.getStopAssignments().size());
        assertEquals("DT:PassengerStopAssignment:HKI", stopsData.getStopAssignments().get(0).id());
    }

    // --- Helpers ---

    private PetiStopSource fixturePetiSource(final List<PetiStop> stops) {
        return () -> stops;
    }

    private Station createStation(final String shortCode, final String name, final int uicCode,
                                   final boolean passengerTraffic, final BigDecimal latitude, final BigDecimal longitude) {
        final Station station = new Station();
        station.shortCode = shortCode;
        station.name = name;
        station.uicCode = uicCode;
        station.passengerTraffic = passengerTraffic;
        station.latitude = latitude;
        station.longitude = longitude;
        station.countryCode = "FI";
        return station;
    }
}
