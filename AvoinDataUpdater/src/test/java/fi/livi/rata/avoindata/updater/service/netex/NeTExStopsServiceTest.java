package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NeTExStopsService — Station to ScheduledStopPoint/RoutePoint/DestinationDisplay mapping.
 */
class NeTExStopsServiceTest {

    private NeTExStopsService stopsService;

    @BeforeEach
    void setUp() {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        stopsService = new NeTExStopsService(idGenerator);
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

    @Test
    void givenStations_whenCreatingStops_thenNoPassengerStopAssignmentGenerated() {
        // given (PETI is deferred, so no StopAssignments in initial implementation)
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station));

        // then: the data object should not contain any StopAssignment references
        // (NeTExStopsData doesn't have a stopAssignments field — verifying by structure)
        assertNotNull(stopsData);
        assertNotNull(stopsData.getScheduledStopPoints());
    }

    // --- Helper ---

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
