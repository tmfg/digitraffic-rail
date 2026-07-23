package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.netex.peti.EmptyPetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
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
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

        // then
        assertEquals("Helsinki asema", stopsData.getScheduledStopPoints().get(0).name());
    }

    @Test
    void givenStation_whenCreatingStops_thenScheduledStopPointContainsCoordinates() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

        // then
        assertEquals("HKI", stopsData.getScheduledStopPoints().get(0).privateCode());
    }

    @Test
    void givenStation_whenCreatingStops_thenProducesRoutePoint() {
        // given
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(passengerStation, nonPassengerStation), nullTrackPairs(List.of(passengerStation, nonPassengerStation)));

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
        final NeTExStopsData stopsData = stopsService.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(matchedStation, unmatchedStation), nullTrackPairs(List.of(matchedStation, unmatchedStation)));

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
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(nonPassengerStation), nullTrackPairs(List.of(nonPassengerStation)));

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
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

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
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), nullTrackPairs(List.of(station)));

        // then — assignment ID follows DT:PassengerStopAssignment:{shortCode}
        assertEquals(1, stopsData.getStopAssignments().size());
        assertEquals("DT:PassengerStopAssignment:HKI", stopsData.getStopAssignments().get(0).id());
    }

    // --- Pass 2b: Track-qualified ScheduledStopPoints and QuayRef scenarios ---

    @Test
    void givenStationAndTrack_whenCreatingStopsWithTrackContext_thenProducesTrackQualifiedSsp() {
        // given — station HKI with commercialTrack "4", PETI stop with quay PublicCode "4"
        final PetiQuay quay4 = new PetiQuay("FSR:Quay:7", "4", null);
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of(quay4));
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("HKI", "4"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — SSP must be track-qualified
        final boolean hasTrackQualifiedSsp = stopsData.getScheduledStopPoints().stream()
                .anyMatch(ssp -> "DT:ScheduledStopPoint:HKI-4".equals(ssp.id()));
        assertTrue(hasTrackQualifiedSsp, "Expected track-qualified SSP 'DT:ScheduledStopPoint:HKI-4'");
    }

    @Test
    void givenStationAndTrackWithMatchingQuay_whenCreatingStops_thenAssignmentHasStopPlaceRefAndQuayRef() {
        // given — station HKI / track "4", PetiStop with quay PublicCode "4" → FSR:Quay:7
        final PetiQuay quay4 = new PetiQuay("FSR:Quay:7", "4", null);
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of(quay4));
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("HKI", "4"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — assignment must have both StopPlaceRef AND QuayRef
        final var assignment = stopsData.getStopAssignments().stream()
                .filter(a -> a.scheduledStopPointRef().contains("HKI"))
                .findFirst();
        assertTrue(assignment.isPresent(), "Expected an assignment for HKI");
        assertEquals("FSR:StopPlace:1", assignment.get().stopPlaceRef());
        assertEquals("FSR:Quay:7", assignment.get().quayRef());
    }

    @Test
    void givenStationAndTrackWithNoMatchingQuay_whenCreatingStops_thenAssignmentHasNoQuayRef() {
        // given — station TPE / track "3", PetiStop has quays but none with PublicCode "3"
        final PetiQuay quay1 = new PetiQuay("FSR:Quay:20", "1", null);
        final PetiQuay quay2 = new PetiQuay("FSR:Quay:21", "2", null);
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:2", 1_000_160, "Tampere", true, null, List.of(quay1, quay2));
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("TPE", "Tampere", 160, true,
                new BigDecimal("61.498500"), new BigDecimal("23.773000"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("TPE", "3"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — assignment has stopPlaceRef but quayRef is null
        final var assignment = stopsData.getStopAssignments().stream()
                .filter(a -> a.scheduledStopPointRef().contains("TPE"))
                .findFirst();
        assertTrue(assignment.isPresent(), "Expected an assignment for TPE");
        assertEquals("FSR:StopPlace:2", assignment.get().stopPlaceRef());
        assertNull(assignment.get().quayRef(), "QuayRef should be null when no matching quay");
        // SSP should still be track-qualified
        final boolean hasTrackSsp = stopsData.getScheduledStopPoints().stream()
                .anyMatch(ssp -> "DT:ScheduledStopPoint:TPE-3".equals(ssp.id()));
        assertTrue(hasTrackSsp, "Expected track-qualified SSP even without quay match");
    }

    @Test
    void givenNullTrackInPair_whenCreatingStops_thenProducesStationLevelSsp() {
        // given — station OL with track null in the schedule data
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:3", 1_000_200, "Oulu", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("OL", "Oulu", 200, true,
                new BigDecimal("65.012700"), new BigDecimal("25.483100"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("OL", null));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — SSP must be station-level (no track suffix)
        final boolean hasStationLevelSsp = stopsData.getScheduledStopPoints().stream()
                .anyMatch(ssp -> "DT:ScheduledStopPoint:OL".equals(ssp.id()));
        assertTrue(hasStationLevelSsp, "Expected station-level SSP 'DT:ScheduledStopPoint:OL'");
        // assignment (if present) should have no QuayRef
        stopsData.getStopAssignments().stream()
                .filter(a -> a.scheduledStopPointRef().contains("OL"))
                .forEach(a -> assertNull(a.quayRef(), "Station-level assignment should have no QuayRef"));
    }

    @Test
    void givenStopPlaceWithNoQuays_whenCreatingStopsWithTrack_thenAssignmentHasNoQuayRef() {
        // given — PetiStop has no quays at all, but track "1" is present in schedule
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:5", 1_000_300, "Seinäjoki", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("SK", "Seinäjoki", 300, true,
                new BigDecimal("62.790000"), new BigDecimal("22.840000"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("SK", "1"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — assignment has stopPlaceRef, but quayRef is null (no quays to match)
        final var assignment = stopsData.getStopAssignments().stream()
                .filter(a -> a.scheduledStopPointRef().contains("SK"))
                .findFirst();
        assertTrue(assignment.isPresent(), "Expected an assignment for SK");
        assertEquals("FSR:StopPlace:5", assignment.get().stopPlaceRef());
        assertNull(assignment.get().quayRef(), "QuayRef should be null when StopPlace has no quays");
    }

    @Test
    void givenUnmatchedStationInPeti_whenCreatingStopsWithTrack_thenSspProducedButNoAssignment() {
        // given — station ABC / track "2" but no matching PetiStop
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:99", 1_000_099, "Other", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("ABC", "Abcville", 500, true,
                new BigDecimal("60.000000"), new BigDecimal("24.000000"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("ABC", "2"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — SSP produced with track-qualified ID, but no assignment
        final boolean hasSsp = stopsData.getScheduledStopPoints().stream()
                .anyMatch(ssp -> "DT:ScheduledStopPoint:ABC-2".equals(ssp.id()));
        assertTrue(hasSsp, "Expected track-qualified SSP for unmatched station");
        final boolean hasAssignment = stopsData.getStopAssignments().stream()
                .anyMatch(a -> a.scheduledStopPointRef().contains("ABC"));
        assertFalse(hasAssignment, "No assignment expected for unmatched station");
    }

    @Test
    void givenMultipleTracksAtSameStation_whenCreatingStops_thenMultipleSspsProduced() {
        // given — station HKI appearing with tracks "4" and "6" in schedule data
        final PetiQuay quay4 = new PetiQuay("FSR:Quay:7", "4", null);
        final PetiQuay quay6 = new PetiQuay("FSR:Quay:9", "6", null);
        final PetiStop petiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of(quay4, quay6));
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(petiStop)));
        final Station station = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));
        final var trackPairs = List.of(
                new NeTExStopsService.StationTrackPair("HKI", "4"),
                new NeTExStopsService.StationTrackPair("HKI", "6"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — two track-qualified SSPs
        final long trackSspCount = stopsData.getScheduledStopPoints().stream()
                .filter(ssp -> ssp.id().equals("DT:ScheduledStopPoint:HKI-4") ||
                               ssp.id().equals("DT:ScheduledStopPoint:HKI-6"))
                .count();
        assertEquals(2, trackSspCount, "Expected 2 track-qualified SSPs for HKI");
    }

    @Test
    void givenMixedTrackResults_whenCreatingStops_thenQuayCountsReflectResolution() {
        // given — three (station, track) tuples:
        //   HKI/4 → quay resolves (matched), TPE/3 → no matching quay (unmatched), OL/null → no track
        final PetiQuay quay4 = new PetiQuay("FSR:Quay:7", "4", null);
        final PetiStop hkiStop = new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of(quay4));
        final PetiQuay quay1 = new PetiQuay("FSR:Quay:20", "1", null);
        final PetiStop tpeStop = new PetiStop("FSR:StopPlace:2", 1_000_160, "Tampere", true, null, List.of(quay1));
        final PetiStop olStop = new PetiStop("FSR:StopPlace:3", 1_000_200, "Oulu", true, null, List.of());
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator,
                fixturePetiSource(List.of(hkiStop, tpeStop, olStop)));
        final Station stationHki = createStation("HKI", "Helsinki asema", 1, true,
                new BigDecimal("60.172133"), new BigDecimal("24.941662"));
        final Station stationTpe = createStation("TPE", "Tampere", 160, true,
                new BigDecimal("61.498500"), new BigDecimal("23.773000"));
        final Station stationOl = createStation("OL", "Oulu", 200, true,
                new BigDecimal("65.012700"), new BigDecimal("25.483100"));
        final var trackPairs = List.of(
                new NeTExStopsService.StationTrackPair("HKI", "4"),
                new NeTExStopsService.StationTrackPair("TPE", "3"),
                new NeTExStopsService.StationTrackPair("OL", null));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(
                List.of(stationHki, stationTpe, stationOl), trackPairs);

        // then — quay counts
        assertEquals(1, stopsData.quayMatchedCount(), "One quay matched (HKI track 4)");
        assertEquals(1, stopsData.quayUnmatchedCount(), "One quay unmatched (TPE track 3)");
        assertEquals(1, stopsData.quayNoTrackCount(), "One no-track (OL null track)");
    }

    @Test
    void givenTervolaWorkedExample_whenCreatingStops_thenAssignmentHasCorrectQuayRef() {
        // given — Tervola: UIC 361, PetiStop "FSR:StopPlace:1" with quay PublicCode "2" → "FSR:Quay:10"
        final PetiQuay quay2 = new PetiQuay("FSR:Quay:10", "2", null);
        final PetiStop tervolaStop = new PetiStop("FSR:StopPlace:1", 1_000_361, "Tervola", true, null, List.of(quay2));
        final NeTExStopsService serviceWithPeti = new NeTExStopsService(idGenerator, fixturePetiSource(List.of(tervolaStop)));
        final Station station = createStation("TRV", "Tervola", 361, true,
                new BigDecimal("66.083000"), new BigDecimal("25.000000"));
        final var trackPairs = List.of(new NeTExStopsService.StationTrackPair("TRV", "2"));

        // when
        final NeTExStopsData stopsData = serviceWithPeti.createStopsData(List.of(station), trackPairs);

        // then — assignment has both StopPlaceRef and QuayRef
        final var assignment = stopsData.getStopAssignments().stream()
                .filter(a -> a.scheduledStopPointRef().contains("TRV"))
                .findFirst();
        assertTrue(assignment.isPresent(), "Expected an assignment for Tervola");
        assertEquals("FSR:StopPlace:1", assignment.get().stopPlaceRef());
        assertEquals("FSR:Quay:10", assignment.get().quayRef());
    }

    // --- Helpers ---

    private static List<NeTExStopsService.StationTrackPair> nullTrackPairs(final List<Station> stations) {
        return stations.stream()
                .map(s -> new NeTExStopsService.StationTrackPair(s.shortCode, null))
                .toList();
    }

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
