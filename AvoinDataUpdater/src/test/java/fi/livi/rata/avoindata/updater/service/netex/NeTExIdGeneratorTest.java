package fi.livi.rata.avoindata.updater.service.netex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("DT:Authority:FIN", result);
    }

    @Test
    void givenOperatorShortCode_whenGeneratingId_thenFollowsConvention() {
        // given
        final String shortCode = "vr";

        // when
        final String result = idGenerator.operatorId(shortCode);

        // then
        assertEquals("DT:Operator:vr", result);
    }

    @Test
    void givenCommuterLineId_whenGeneratingLineId_thenFollowsConvention() {
        // given
        final String lineIdentifier = "Z";

        // when
        final String result = idGenerator.lineId(lineIdentifier);

        // then
        assertEquals("DT:Line:Z", result);
    }

    @Test
    void givenTrainTypeAsLine_whenGeneratingLineId_thenFollowsConvention() {
        // given
        final String lineIdentifier = "IC";

        // when
        final String result = idGenerator.lineId(lineIdentifier);

        // then
        assertEquals("DT:Line:IC", result);
    }

    @Test
    void givenRouteComponents_whenGeneratingRouteId_thenIncludesAllParts() {
        // given
        final String lineId = "Z";
        final String hash = "a3b2";

        // when
        final String result = idGenerator.routeId(lineId, hash);

        // then
        assertEquals("DT:Route:Z-a3b2", result);
    }

    @Test
    void givenStationShortCode_whenGeneratingRoutePointId_thenFollowsConvention() {
        // given
        final String stationShortCode = "HKI";

        // when
        final String result = idGenerator.routePointId(stationShortCode);

        // then
        assertEquals("DT:RoutePoint:HKI", result);
    }

    @Test
    void givenStationShortCode_whenGeneratingScheduledStopPointId_thenFollowsConvention() {
        // given
        final String stationShortCode = "HKI";

        // when
        final String result = idGenerator.scheduledStopPointId(stationShortCode);

        // then
        assertEquals("DT:ScheduledStopPoint:HKI", result);
    }

    @Test
    void givenLineAndHash_whenGeneratingJourneyPatternId_thenFollowsConvention() {
        // given
        final String lineId = "Z";
        final String hash = "1234";

        // when
        final String result = idGenerator.journeyPatternId(lineId, hash);

        // then
        assertEquals("DT:JourneyPattern:Z-1234", result);
    }

    @Test
    void givenRegularSchedule_whenGeneratingServiceJourneyId_thenUsesScheduleId() {
        // given
        final long trainNumber = 59;
        final long scheduleId = 12345;

        // when
        final String result = idGenerator.serviceJourneyId(trainNumber, scheduleId);

        // then
        assertEquals("DT:ServiceJourney:59-12345", result);
    }

    @Test
    void givenAdhocSchedule_whenGeneratingServiceJourneyId_thenUsesDate() {
        // given
        final long trainNumber = 59;
        final LocalDate date = LocalDate.of(2026, 6, 25);

        // when
        final String result = idGenerator.serviceJourneyIdAdhoc(trainNumber, date);

        // then
        assertEquals("DT:ServiceJourney:59-2026-06-25", result);
    }

    @Test
    void givenDayTypeHash_whenGeneratingId_thenFollowsConvention() {
        // given
        final String hash = "MoTuWeThFr-20260615-20261214";

        // when
        final String result = idGenerator.dayTypeId(hash);

        // then
        assertEquals("DT:DayType:MoTuWeThFr-20260615-20261214", result);
    }

    @Test
    void givenStationShortCode_whenGeneratingDestinationDisplayId_thenFollowsConvention() {
        // given
        final String stationShortCode = "TPE";

        // when
        final String result = idGenerator.destinationDisplayId(stationShortCode);

        // then
        assertEquals("DT:DestinationDisplay:TPE", result);
    }

    @Test
    void givenNetworkCode_whenGeneratingId_thenFollowsConvention() {
        // given
        final String code = "FIN-RAIL";

        // when
        final String result = idGenerator.networkId(code);

        // then
        assertEquals("DT:Network:FIN-RAIL", result);
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
        assertTrue(authorityId.startsWith("DT:"));
        assertTrue(operatorId.startsWith("DT:"));
        assertTrue(lineId.startsWith("DT:"));
    }

    // --- Composition-related IDs ---

    @Test
    void givenTypeName_whenGeneratingVehicleTypeId_thenFollowsConvention() {
        // given
        final String typeName = "Sr2";

        // when
        final String result = idGenerator.vehicleTypeId(typeName);

        // then
        assertEquals("DT:VehicleType:Sr2", result);
    }

    @Test
    void givenWagonTypeName_whenGeneratingVehicleTypeId_thenFollowsConvention() {
        // given
        final String typeName = "Ed";

        // when
        final String result = idGenerator.vehicleTypeId(typeName);

        // then
        assertEquals("DT:VehicleType:Ed", result);
    }

    @Test
    void givenTrainWithBeginStation_whenGeneratingDatedVehicleJourneyId_thenIncludesStation() {
        // given
        final long trainNumber = 59;
        final LocalDate date = LocalDate.of(2026, 7, 7);
        final String beginStation = "HKI";

        // when
        final String result = idGenerator.datedVehicleJourneyId(trainNumber, date, beginStation);

        // then
        assertEquals("DT:DatedVehicleJourney:59-2026-07-07-HKI", result);
    }

    @Test
    void givenTrainWithoutBeginStation_whenGeneratingDatedVehicleJourneyId_thenOmitsStation() {
        // given
        final long trainNumber = 59;
        final LocalDate date = LocalDate.of(2026, 7, 7);

        // when
        final String result = idGenerator.datedVehicleJourneyId(trainNumber, date, null);

        // then
        assertEquals("DT:DatedVehicleJourney:59-2026-07-07", result);
    }
}
