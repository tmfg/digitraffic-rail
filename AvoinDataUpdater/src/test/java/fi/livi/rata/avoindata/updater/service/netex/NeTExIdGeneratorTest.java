package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for NeTExIdGenerator — NeTEx ID generation following
 * {Codespace}:{ElementType}:{localId} convention.
 */
class NeTExIdGeneratorTest {

    private NeTExIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new NeTExIdGenerator();
    }

    @Test
    void givenAuthorityCode_whenGeneratingId_thenFollowsConvention() {
        // given
        final String code = "FIN";

        // when
        final String result = idGenerator.authorityId(code);

        // then
        assertEquals("FTR:Authority:FIN", result);
    }

    @Test
    void givenOperatorShortCode_whenGeneratingId_thenFollowsConvention() {
        // given
        final String shortCode = "vr";

        // when
        final String result = idGenerator.operatorId(shortCode);

        // then
        assertEquals("FTR:Operator:vr", result);
    }

    @Test
    void givenCommuterLineId_whenGeneratingLineId_thenFollowsConvention() {
        // given
        final String lineIdentifier = "Z";

        // when
        final String result = idGenerator.lineId(lineIdentifier);

        // then
        assertEquals("FTR:Line:Z", result);
    }

    @Test
    void givenTrainTypeAsLine_whenGeneratingLineId_thenFollowsConvention() {
        // given
        final String lineIdentifier = "IC";

        // when
        final String result = idGenerator.lineId(lineIdentifier);

        // then
        assertEquals("FTR:Line:IC", result);
    }

    @Test
    void givenRouteComponents_whenGeneratingRouteId_thenIncludesAllParts() {
        // given
        final String lineId = "Z";
        final String hash = "a3b2";

        // when
        final String result = idGenerator.routeId(lineId, hash);

        // then
        assertEquals("FTR:Route:Z_a3b2", result);
    }

    @Test
    void givenStationShortCode_whenGeneratingRoutePointId_thenFollowsConvention() {
        // given
        final String stationShortCode = "HKI";

        // when
        final String result = idGenerator.routePointId(stationShortCode);

        // then
        assertEquals("FTR:RoutePoint:HKI", result);
    }

    @Test
    void givenStationShortCode_whenGeneratingScheduledStopPointId_thenFollowsConvention() {
        // given
        final String stationShortCode = "HKI";

        // when
        final String result = idGenerator.scheduledStopPointId(stationShortCode);

        // then
        assertEquals("FTR:ScheduledStopPoint:HKI", result);
    }

    @Test
    void givenLineAndHash_whenGeneratingJourneyPatternId_thenFollowsConvention() {
        // given
        final String lineId = "Z";
        final String hash = "1234";

        // when
        final String result = idGenerator.journeyPatternId(lineId, hash);

        // then
        assertEquals("FTR:JourneyPattern:Z_1234", result);
    }

    @Test
    void givenRegularSchedule_whenGeneratingServiceJourneyId_thenUsesScheduleId() {
        // given
        final long trainNumber = 59;
        final long scheduleId = 12345;

        // when
        final String result = idGenerator.serviceJourneyId(trainNumber, scheduleId);

        // then
        assertEquals("FTR:ServiceJourney:59-12345", result);
    }

    @Test
    void givenAdhocSchedule_whenGeneratingServiceJourneyId_thenUsesDate() {
        // given
        final long trainNumber = 59;
        final LocalDate date = LocalDate.of(2026, 6, 25);

        // when
        final String result = idGenerator.serviceJourneyIdAdhoc(trainNumber, date);

        // then
        assertEquals("FTR:ServiceJourney:59-2026-06-25", result);
    }

    @Test
    void givenDayTypeHash_whenGeneratingId_thenFollowsConvention() {
        // given
        final String hash = "MoTuWeThFr-20260615-20261214";

        // when
        final String result = idGenerator.dayTypeId(hash);

        // then
        assertEquals("FTR:DayType:MoTuWeThFr-20260615-20261214", result);
    }

    @Test
    void givenStationShortCode_whenGeneratingDestinationDisplayId_thenFollowsConvention() {
        // given
        final String stationShortCode = "TPE";

        // when
        final String result = idGenerator.destinationDisplayId(stationShortCode);

        // then
        assertEquals("FTR:DestinationDisplay:TPE", result);
    }

    @Test
    void givenNetworkCode_whenGeneratingId_thenFollowsConvention() {
        // given
        final String code = "FIN-RAIL";

        // when
        final String result = idGenerator.networkId(code);

        // then
        assertEquals("FTR:Network:FIN-RAIL", result);
    }

    @Test
    void givenDayTypeHash_whenGeneratedMultipleTimes_thenIsDeterministic() {
        // given
        final String hash = "MoTuWeThFr-20260615-20261214";

        // when
        final String result1 = idGenerator.dayTypeId(hash);
        final String result2 = idGenerator.dayTypeId(hash);

        // then
        assertEquals(result1, result2);
    }

    @Test
    void givenAnyId_whenGenerated_thenStartsWithCodespace() {
        // given/when
        final String authorityId = idGenerator.authorityId("FIN");
        final String operatorId = idGenerator.operatorId("vr");
        final String lineId = idGenerator.lineId("Z");

        // then
        assertTrue(authorityId.startsWith("FTR:"));
        assertTrue(operatorId.startsWith("FTR:"));
        assertTrue(lineId.startsWith("FTR:"));
    }

    // --- Composition-related IDs ---

    @Test
    void givenTypeName_whenGeneratingVehicleTypeId_thenFollowsConvention() {
        // given
        final String typeName = "Sr2";

        // when
        final String result = idGenerator.vehicleTypeId(typeName);

        // then
        assertEquals("FTR:VehicleType:Sr2", result);
    }

    @Test
    void givenWagonTypeName_whenGeneratingVehicleTypeId_thenFollowsConvention() {
        // given
        final String typeName = "Ed";

        // when
        final String result = idGenerator.vehicleTypeId(typeName);

        // then
        assertEquals("FTR:VehicleType:Ed", result);
    }

    @Test
    void givenTrainWithBeginStation_whenGeneratingDatedServiceJourneyId_thenIncludesStation() {
        // given
        final long trainNumber = 59;
        final LocalDate date = LocalDate.of(2026, 7, 7);
        final String beginStation = "HKI";

        // when
        final String result = idGenerator.datedServiceJourneyId(trainNumber, date, beginStation);

        // then
        assertEquals("FTR:DatedServiceJourney:59-2026-07-07-HKI", result);
    }

    @Test
    void givenTrainWithoutBeginStation_whenGeneratingDatedServiceJourneyId_thenOmitsStation() {
        // given
        final long trainNumber = 59;
        final LocalDate date = LocalDate.of(2026, 7, 7);

        // when
        final String result = idGenerator.datedServiceJourneyId(trainNumber, date, null);

        // then
        assertEquals("FTR:DatedServiceJourney:59-2026-07-07", result);
    }

    // --- Pass 2: PassengerStopAssignment ID ---

    @Test
    void givenStationShortCode_whenGeneratingPassengerStopAssignmentId_thenFollowsConvention() {
        // given
        final String stationShortCode = "HKI";

        // when
        final String result = idGenerator.passengerStopAssignmentId(stationShortCode);

        // then
        assertEquals("FTR:PassengerStopAssignment:HKI", result);
    }

    // --- Pass 2b: Track-qualified IDs ---

    @Test
    void givenStationAndTrack_whenGeneratingScheduledStopPointId_thenReturnsTrackQualifiedId() {
        // given
        final String shortCode = "HKI";
        final String track = "4";

        // when
        final String result = idGenerator.scheduledStopPointId(shortCode, track);

        // then
        assertEquals("FTR:ScheduledStopPoint:HKI-4", result);
    }

    @Test
    void givenStationAndNullTrack_whenGeneratingScheduledStopPointId_thenReturnsStationLevelId() {
        // given
        final String shortCode = "HKI";

        // when
        final String result = idGenerator.scheduledStopPointId(shortCode, null);

        // then
        assertEquals("FTR:ScheduledStopPoint:HKI", result);
    }

    @Test
    void givenStationAndBlankTrack_whenGeneratingScheduledStopPointId_thenReturnsStationLevelId() {
        // given
        final String shortCode = "HKI";

        // when
        final String result = idGenerator.scheduledStopPointId(shortCode, "");

        // then
        assertEquals("FTR:ScheduledStopPoint:HKI", result);
    }

    @Test
    void givenStationAndTrack_whenGeneratingPassengerStopAssignmentId_thenReturnsTrackQualifiedId() {
        // given
        final String shortCode = "TPE";
        final String track = "1";

        // when
        final String result = idGenerator.passengerStopAssignmentId(shortCode, track);

        // then
        assertEquals("FTR:PassengerStopAssignment:TPE-1", result);
    }

    @Test
    void givenTrackQualifiedJourneyPattern_whenGeneratingStopPointId_thenKeepsWholeLocalId() {
        // given: track-qualified patterns embed the whole stop sequence, so the
        // local id itself contains separators
        final String journeyPatternId = idGenerator.journeyPatternId("IC-56", "OL-1_PSL-5_HKI-8");

        // when
        final String result = idGenerator.stopPointInJourneyPatternId(journeyPatternId, 1);

        // then
        assertEquals("FTR:StopPointInJourneyPattern:IC-56_OL-1_PSL-5_HKI-8_1", result);
    }

    @Test
    void givenPatternsDifferingOnlyBeforeLastTrack_whenGeneratingStopPointIds_thenIdsDiffer() {
        // given: two patterns of the same Line ending at the same track — the case
        // that collided in production
        final String viaHameenlinna = idGenerator.journeyPatternId("IC-56", "OL-1_HM-1_HKI-8");
        final String direct = idGenerator.journeyPatternId("IC-56", "OL-1_HKI-8");

        // when
        final String first = idGenerator.stopPointInJourneyPatternId(viaHameenlinna, 1);
        final String second = idGenerator.stopPointInJourneyPatternId(direct, 1);

        // then
        assertNotEquals(first, second);
    }
}
