package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * Tests for NeTExEntityService — Lines, Operators, ServiceJourneys creation.
 */
class NeTExEntityServiceTest {

    private NeTExEntityService entityService;
    private NeTExIdGenerator idGenerator;

    private NeTExRouteService routeService;

    @BeforeEach
    void setUp() {
        idGenerator = new NeTExIdGenerator();
        final NeTExTimeConverter timeConverter = new NeTExTimeConverter();
        entityService = new NeTExEntityService(idGenerator, timeConverter);
        routeService = new NeTExRouteService(idGenerator);
    }

    // --- Line derivation ---

    @Test
    void givenCommuterTrainWithLineId_whenDerivingLine_thenUsesCommuterLineId() {
        // given
        final Schedule schedule = createCommuterSchedule(1L, 2105L, "Z");

        // when
        final String lineId = entityService.deriveLineId(schedule);

        // then
        assertEquals("Z", lineId);
    }

    @Test
    void givenLongDistanceTrainWithoutLineId_whenDerivingLine_thenUsesTrainTypeAndNumber() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");

        // when
        final String lineId = entityService.deriveLineId(schedule);

        // then
        assertEquals("IC-59", lineId);
    }

    @Test
    void givenPendolino_whenDerivingLine_thenUsesTrainTypeAndNumber() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 9L, "S");

        // when
        final String lineId = entityService.deriveLineId(schedule);

        // then
        assertEquals("S-9", lineId);
    }

    @Test
    void givenSchedules_whenCreatingLines_thenAllHaveRailTransportMode() {
        // given
        final Schedule schedule = createCommuterSchedule(1L, 2105L, "Z");

        // when
        final List<NeTExEntityService.NeTExLine> lines = entityService.createLines(List.of(schedule),
                emptyRouteData(), Map.of());

        // then
        assertEquals(1, lines.size());
        assertEquals("rail", lines.get(0).transportMode());
    }

    @Test
    void givenCommuterSchedule_whenCreatingLines_thenLineIdIsCorrect() {
        // given
        final Schedule schedule = createCommuterSchedule(1L, 2105L, "Z");

        // when
        final List<NeTExEntityService.NeTExLine> lines = entityService.createLines(List.of(schedule),
                emptyRouteData(), Map.of());

        // then
        assertEquals("FTR:Line:Z", lines.get(0).id());
        assertEquals("Z", lines.get(0).publicCode());
    }

    @Test
    void givenMultipleSchedulesSameLine_whenCreatingLines_thenDeduplicatesToOneLineElement() {
        // given: two Z-trains
        final Schedule schedule1 = createCommuterSchedule(1L, 2105L, "Z");
        final Schedule schedule2 = createCommuterSchedule(2L, 2107L, "Z");

        // when
        final List<NeTExEntityService.NeTExLine> lines = entityService.createLines(List.of(schedule1, schedule2),
                emptyRouteData(), Map.of());

        // then
        assertEquals(1, lines.size());
    }

    // --- Operator mapping ---

    @Test
    void givenScheduleWithOperator_whenCreatingOperators_thenProducesOperatorWithCorrectId() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");

        // when
        final List<NeTExEntityService.NeTExOperator> operators = entityService.createOperators(List.of(schedule));

        // then
        assertEquals(1, operators.size());
        assertEquals("FTR:Operator:vr", operators.get(0).id());
    }

    @Test
    void givenScheduleWithOperator_whenCreatingOperators_thenCompanyNumberIsUicCode() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");

        // when
        final List<NeTExEntityService.NeTExOperator> operators = entityService.createOperators(List.of(schedule));

        // then
        assertEquals(10, operators.get(0).companyNumber());
    }

    @Test
    void givenScheduleWithOperator_whenCreatingOperators_thenPrivateCodeIsShortCode() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");

        // when
        final List<NeTExEntityService.NeTExOperator> operators = entityService.createOperators(List.of(schedule));

        // then
        assertEquals("vr", operators.get(0).privateCode());
    }

    // --- ServiceJourney mapping ---

    @Test
    void givenRegularSchedule_whenCreatingServiceJourneys_thenIdUsesScheduleId() {
        // given
        final Schedule schedule = createLongDistanceSchedule(12345L, 59L, "IC");
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertEquals(1, journeys.size());
        assertEquals("FTR:ServiceJourney:59-12345", journeys.get(0).id());
    }

    @Test
    void givenAdhocSchedule_whenCreatingServiceJourneys_thenIdUsesDate() {
        // given
        final Schedule schedule = createAdhocSchedule(200L, 59L, "IC", LocalDate.of(2026, 6, 25));
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertEquals(1, journeys.size());
        assertEquals("FTR:ServiceJourney:59-2026-06-25", journeys.get(0).id());
    }

    @Test
    void givenSchedule_whenCreatingServiceJourneys_thenNameIsTrainTypeAndNumber() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertEquals("IC 59", journeys.get(0).name());
    }

    @Test
    void givenSchedule_whenCreatingServiceJourneys_thenPrivateCodeIsTrainNumber() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertEquals("59", journeys.get(0).privateCode());
    }

    @Test
    void givenSchedule_whenCreatingServiceJourneys_thenReferencesJourneyPattern() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertNotNull(journeys.get(0).journeyPatternRef());
        assertTrue(journeys.get(0).journeyPatternRef().startsWith("FTR:JourneyPattern:"));
    }

    @Test
    void givenSchedule_whenCreatingServiceJourneys_thenReferencesOperator() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertEquals("FTR:Operator:vr", journeys.get(0).operatorRef());
    }

    @Test
    void givenSchedule_whenCreatingServiceJourneys_thenReferencesLine() {
        // given
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then
        assertEquals("FTR:Line:IC-59", journeys.get(0).lineRef());
    }

    @Test
    void givenRegularScheduleCovering180Days_whenCreatingServiceJourneys_thenProducesExactlyOneServiceJourney() {
        // given: one Schedule covering 180 operating days (not one SJ per date)
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        schedule.startDate = LocalDate.of(2026, 6, 15);
        schedule.endDate = LocalDate.of(2026, 12, 14);

        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then: exactly one ServiceJourney (not 180!)
        assertEquals(1, journeys.size());
    }

    // --- Pass 2b: Passing times carry station and track context ---

    @Test
    void givenScheduleWithTracks_whenCreatingServiceJourneys_thenPassingTimesCarryStationAndTrack() {
        // given — schedule with HKI (track "4"), TPE (track "1"), OL (track "2")
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        setTracksOnSchedule(schedule, List.of("4", "1", "2"));
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then — passing times carry station short code and commercial track
        final var passingTimes = journeys.get(0).passingTimes();
        assertEquals("HKI", passingTimes.get(0).stationShortCode());
        assertEquals("4", passingTimes.get(0).commercialTrack());
        assertEquals("TPE", passingTimes.get(1).stationShortCode());
        assertEquals("1", passingTimes.get(1).commercialTrack());
        assertEquals("OL", passingTimes.get(2).stationShortCode());
        assertEquals("2", passingTimes.get(2).commercialTrack());
    }

    @Test
    void givenScheduleWithNullTrack_whenCreatingServiceJourneys_thenPassingTimeHasNullTrack() {
        // given — schedule where TPE has commercialTrack = null
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        setTracksOnSchedule(schedule, Arrays.asList("4", null, "2"));
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then — TPE's passing time has null commercialTrack
        final var passingTimes = journeys.get(0).passingTimes();
        assertNull(passingTimes.get(1).commercialTrack(), "TPE should have null commercialTrack");
        assertEquals("TPE", passingTimes.get(1).stationShortCode());
    }

    @Test
    void givenScheduleWithTracks_whenCreatingServiceJourneys_thenStillReferencesJourneyPattern() {
        // given — schedule with track data
        final Schedule schedule = createLongDistanceSchedule(1L, 59L, "IC");
        setTracksOnSchedule(schedule, List.of("4", "1", "2"));
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // when
        final List<NeTExEntityService.NeTExServiceJourney> journeys = entityService
                .createServiceJourneys(List.of(schedule), routeData);

        // then — journey pattern ref is still set correctly
        assertNotNull(journeys.get(0).journeyPatternRef());
        assertTrue(journeys.get(0).journeyPatternRef().startsWith("FTR:JourneyPattern:"));
    }

    // --- Helpers ---

    private static NeTExRouteData emptyRouteData() {
        return new NeTExRouteData(List.of(), List.of(), Map.of());
    }

    private Schedule createCommuterSchedule(final long id, final long trainNumber, final String commuterLineId) {
        final Schedule schedule = createBaseSchedule(id, trainNumber, "HDM", "Commuter");
        schedule.commuterLineId = commuterLineId;
        return schedule;
    }

    private Schedule createLongDistanceSchedule(final long id, final long trainNumber, final String trainTypeName) {
        return createBaseSchedule(id, trainNumber, trainTypeName, "Long-distance");
    }

    private Schedule createAdhocSchedule(final long id, final long trainNumber, final String trainTypeName,
            final LocalDate date) {
        final Schedule schedule = createBaseSchedule(id, trainNumber, trainTypeName, "Long-distance");
        schedule.timetableType = Train.TimetableType.ADHOC;
        schedule.startDate = date;
        schedule.endDate = date;
        return schedule;
    }

    private Schedule createBaseSchedule(final long id, final long trainNumber,
            final String trainTypeName, final String categoryName) {
        final Schedule schedule = new Schedule();
        schedule.id = id;
        schedule.trainNumber = trainNumber;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = LocalDate.of(2026, 6, 15);
        schedule.endDate = LocalDate.of(2026, 12, 14);
        schedule.runOnMonday = true;
        schedule.runOnTuesday = true;
        schedule.runOnWednesday = true;
        schedule.runOnThursday = true;
        schedule.runOnFriday = true;
        schedule.runOnSaturday = false;
        schedule.runOnSunday = false;
        schedule.scheduleCancellations = new HashSet<>();
        schedule.scheduleExceptions = new HashSet<>();
        schedule.operator = new Operator(10, "vr");

        final TrainType trainType = new TrainType();
        trainType.name = trainTypeName;
        trainType.commercial = true;
        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = categoryName;
        trainType.trainCategory = trainCategory;
        schedule.trainType = trainType;
        schedule.trainCategory = trainCategory;
        schedule.commuterLineId = null;

        // Default stops: HKI → TPE → OL
        schedule.scheduleRows = createDefaultStops();

        return schedule;
    }

    private List<ScheduleRow> createDefaultStops() {
        final List<ScheduleRow> rows = new ArrayList<>();
        final String[] stations = { "HKI", "TPE", "OL" };

        for (int i = 0; i < stations.length; i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = i + 1;
            row.station = new StationEmbeddable(stations[i], 100 + i, "FI");

            if (i > 0) {
                final ScheduleRowPart arrival = new ScheduleRowPart();
                arrival.id = i * 10 + 1;
                arrival.timestamp = Duration.ofHours(5).plusMinutes(30 + i * 60);
                arrival.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                arrival.scheduleRow = row;
                row.arrival = arrival;
            }
            if (i < stations.length - 1) {
                final ScheduleRowPart departure = new ScheduleRowPart();
                departure.id = i * 10 + 2;
                departure.timestamp = Duration.ofHours(5).plusMinutes(31 + i * 60);
                departure.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                departure.scheduleRow = row;
                row.departure = departure;
            }

            rows.add(row);
        }
        return rows;
    }

    private void setTracksOnSchedule(final Schedule schedule, final List<String> tracks) {
        for (int i = 0; i < schedule.scheduleRows.size(); i++) {
            schedule.scheduleRows.get(i).commercialTrack = tracks.get(i);
        }
    }
}
