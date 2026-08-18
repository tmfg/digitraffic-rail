package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NeTExRouteService — Route and JourneyPattern derivation from schedules.
 */
class NeTExRouteServiceTest {

    private NeTExRouteService routeService;

    @BeforeEach
    void setUp() {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        routeService = new NeTExRouteService(idGenerator);
    }

    @Test
    void givenScheduleWithStops_whenCreatingRouteData_thenProducesRouteWithCorrectPoints() {
        // given: a schedule with stops HKI → TPE → OL
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        assertEquals(1, routeData.getRoutes().size());
        assertEquals(3, routeData.getRoutes().get(0).routePointRefs().size());
        assertEquals("FTR:RoutePoint:HKI", routeData.getRoutes().get(0).routePointRefs().get(0));
        assertEquals("FTR:RoutePoint:TPE", routeData.getRoutes().get(0).routePointRefs().get(1));
        assertEquals("FTR:RoutePoint:OL", routeData.getRoutes().get(0).routePointRefs().get(2));
    }

    @Test
    void givenSchedule_whenCreatingRouteData_thenRouteNameIsOriginDashDestination() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then: name is "{first} - {last}" station short codes (or names if available)
        final String routeName = routeData.getRoutes().get(0).name();
        assertNotNull(routeName);
        assertTrue(routeName.contains("HKI") || routeName.contains("OL"));
    }

    @Test
    void givenSchedule_whenCreatingRouteData_thenRouteIdFollowsConvention() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        assertTrue(routeData.getRoutes().get(0).id().startsWith("FTR:Route:"));
    }

    @Test
    void givenSchedule_whenCreatingRouteData_thenRouteReferencesLine() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        assertEquals("FTR:Line:IC", routeData.getRoutes().get(0).lineRef());
    }

    @Test
    void givenScheduleWithCommercialStops_whenCreatingRouteData_thenJourneyPatternContainsOnlyCommercialStops() {
        // given: HKI (commercial) → PSL (non-commercial, pass-through) → TPE (commercial) → OL (commercial)
        final Schedule schedule = createScheduleWithMixedStops(1L, 59L, "IC", "Long-distance");

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then: JourneyPattern has only commercial stops (3)
        assertEquals(1, routeData.getJourneyPatterns().size());
        assertEquals(3, routeData.getJourneyPatterns().get(0).stopPoints().size());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenIdFollowsConvention() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        assertTrue(routeData.getJourneyPatterns().get(0).id().startsWith("FTR:JourneyPattern:"));
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenReferencesRoute() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        final String routeId = routeData.getRoutes().get(0).id();
        assertEquals(routeId, routeData.getJourneyPatterns().get(0).routeRef());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenFirstStopHasForAlightingFalse() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then: first stop — cannot alight (it's the origin)
        final var firstStop = routeData.getJourneyPatterns().get(0).stopPoints().get(0);
        assertFalse(firstStop.forAlighting());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenLastStopHasForBoardingFalse() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then: last stop — cannot board (it's the terminus)
        final var stops = routeData.getJourneyPatterns().get(0).stopPoints();
        final var lastStop = stops.get(stops.size() - 1);
        assertFalse(lastStop.forBoarding());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenIntermediateStopsAllowBoardingAndAlighting() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then: middle stop allows both
        final var middleStop = routeData.getJourneyPatterns().get(0).stopPoints().get(1);
        assertTrue(middleStop.forBoarding());
        assertTrue(middleStop.forAlighting());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenFirstStopHasDestinationDisplayRef() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then: first stop shows destination
        final var firstStop = routeData.getJourneyPatterns().get(0).stopPoints().get(0);
        assertEquals("FTR:DestinationDisplay:OL", firstStop.destinationDisplayRef());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenStopOrderIsSequential() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        final var stops = routeData.getJourneyPatterns().get(0).stopPoints();
        assertEquals(1, stops.get(0).order());
        assertEquals(2, stops.get(1).order());
        assertEquals(3, stops.get(2).order());
    }

    @Test
    void givenSchedule_whenCreatingJourneyPattern_thenStopRefsAreCorrect() {
        // given
        final Schedule schedule = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then
        final var stops = routeData.getJourneyPatterns().get(0).stopPoints();
        assertEquals("FTR:ScheduledStopPoint:HKI", stops.get(0).scheduledStopPointRef());
        assertEquals("FTR:ScheduledStopPoint:TPE", stops.get(1).scheduledStopPointRef());
        assertEquals("FTR:ScheduledStopPoint:OL", stops.get(2).scheduledStopPointRef());
    }

    @Test
    void givenTwoTrainsWithSameStopSequence_whenCreatingRouteData_thenShareJourneyPattern() {
        // given
        final Schedule schedule1 = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));
        final Schedule schedule2 = createScheduleWithStops(2L, 61L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule1, schedule2));

        // then: only one JourneyPattern
        assertEquals(1, routeData.getJourneyPatterns().size());
        assertEquals(routeData.getJourneyPatternIdForSchedule(1L), routeData.getJourneyPatternIdForSchedule(2L));
    }

    @Test
    void givenTwoTrainsWithDifferentStopSequences_whenCreatingRouteData_thenSeparateJourneyPatterns() {
        // given
        final Schedule schedule1 = createScheduleWithStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));
        final Schedule schedule2 = createScheduleWithStops(2L, 63L, "IC", "Long-distance",
                List.of("HKI", "TPE", "JY"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule1, schedule2));

        // then
        assertEquals(2, routeData.getJourneyPatterns().size());
        assertNotEquals(routeData.getJourneyPatternIdForSchedule(1L), routeData.getJourneyPatternIdForSchedule(2L));
    }

    // --- Pass 2b: Track-qualified journey pattern scenarios ---

    @Test
    void givenScheduleWithTracks_whenCreatingTrackAwareRouteData_thenStopRefsAreTrackQualified() {
        // given — schedule with stops HKI (track "4"), TPE (track "1"), OL (track "2")
        final Schedule schedule = createScheduleWithTrackedStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), List.of("4", "1", "2"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then — journey pattern stop refs are track-qualified
        final var stops = routeData.getJourneyPatterns().get(0).stopPoints();
        assertEquals("FTR:ScheduledStopPoint:HKI-4", stops.get(0).scheduledStopPointRef());
        assertEquals("FTR:ScheduledStopPoint:TPE-1", stops.get(1).scheduledStopPointRef());
        assertEquals("FTR:ScheduledStopPoint:OL-2", stops.get(2).scheduledStopPointRef());
    }

    @Test
    void givenScheduleWithNullTrack_whenCreatingTrackAwareRouteData_thenFallsBackToStationLevel() {
        // given — HKI track "4", TPE track null, OL track "2"
        final Schedule schedule = createScheduleWithTrackedStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), Arrays.asList("4", null, "2"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then — TPE falls back to station-level SSP
        final var stops = routeData.getJourneyPatterns().get(0).stopPoints();
        assertEquals("FTR:ScheduledStopPoint:HKI-4", stops.get(0).scheduledStopPointRef());
        assertEquals("FTR:ScheduledStopPoint:TPE", stops.get(1).scheduledStopPointRef());
        assertEquals("FTR:ScheduledStopPoint:OL-2", stops.get(2).scheduledStopPointRef());
    }

    @Test
    void givenTwoTrainsSameStationsDifferentTracks_whenCreatingTrackAwareRouteData_thenSeparatePatterns() {
        // given — same stations, different tracks at HKI
        final Schedule schedule1 = createScheduleWithTrackedStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), List.of("4", "1", "2"));
        final Schedule schedule2 = createScheduleWithTrackedStops(2L, 61L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), List.of("6", "1", "2"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule1, schedule2));

        // then — two distinct journey patterns (hash differs due to track at HKI)
        assertEquals(2, routeData.getJourneyPatterns().size());
        assertNotEquals(routeData.getJourneyPatternIdForSchedule(1L), routeData.getJourneyPatternIdForSchedule(2L));
    }

    @Test
    void givenTwoTrainsSameStationsAndTracks_whenCreatingTrackAwareRouteData_thenSharePattern() {
        // given — same stations AND same tracks
        final Schedule schedule1 = createScheduleWithTrackedStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), List.of("4", "1", "2"));
        final Schedule schedule2 = createScheduleWithTrackedStops(2L, 61L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), List.of("4", "1", "2"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule1, schedule2));

        // then — they share one journey pattern
        assertEquals(1, routeData.getJourneyPatterns().size());
        assertEquals(routeData.getJourneyPatternIdForSchedule(1L), routeData.getJourneyPatternIdForSchedule(2L));
    }

    @Test
    void givenTrackedSchedule_whenCreatingTrackAwareRouteData_thenRoutePointsRemainStationLevel() {
        // given — schedule with track-qualified stops
        final Schedule schedule = createScheduleWithTrackedStops(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"), List.of("4", "1", "2"));

        // when
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(List.of(schedule));

        // then — route points are STILL station-level (not track-qualified)
        final var routePointRefs = routeData.getRoutes().get(0).routePointRefs();
        assertEquals("FTR:RoutePoint:HKI", routePointRefs.get(0));
        assertEquals("FTR:RoutePoint:TPE", routePointRefs.get(1));
        assertEquals("FTR:RoutePoint:OL", routePointRefs.get(2));
    }

    // --- Helpers ---

    private Schedule createScheduleWithStops(final long id, final long trainNumber, final String trainTypeName,
                                              final String categoryName, final List<String> stationCodes) {
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

        schedule.scheduleRows = new ArrayList<>();
        long rowId = 1;
        for (int i = 0; i < stationCodes.size(); i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = rowId++;
            row.station = new StationEmbeddable(stationCodes.get(i), 100 + i, "FI");

            if (i > 0) {
                // arrival
                final ScheduleRowPart arrival = new ScheduleRowPart();
                arrival.id = rowId++;
                arrival.timestamp = Duration.ofHours(5).plusMinutes(30 + i * 15);
                arrival.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                arrival.scheduleRow = row;
                row.arrival = arrival;
            }
            if (i < stationCodes.size() - 1) {
                // departure
                final ScheduleRowPart departure = new ScheduleRowPart();
                departure.id = rowId++;
                departure.timestamp = Duration.ofHours(5).plusMinutes(31 + i * 15);
                departure.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                departure.scheduleRow = row;
                row.departure = departure;
            }

            schedule.scheduleRows.add(row);
        }

        return schedule;
    }

    private Schedule createScheduleWithMixedStops(final long id, final long trainNumber,
                                                   final String trainTypeName, final String categoryName) {
        final Schedule schedule = createScheduleWithStops(id, trainNumber, trainTypeName, categoryName,
                List.of("HKI", "PSL", "TPE", "OL"));

        // Make PSL (index 1) non-commercial (pass-through)
        final ScheduleRow pslRow = schedule.scheduleRows.get(1);
        if (pslRow.arrival != null) {
            pslRow.arrival.stopType = ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;
        }
        if (pslRow.departure != null) {
            pslRow.departure.stopType = ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;
        }

        return schedule;
    }

    private Schedule createScheduleWithTrackedStops(final long id, final long trainNumber,
                                                     final String trainTypeName, final String categoryName,
                                                     final List<String> stationCodes, final List<String> tracks) {
        final Schedule schedule = createScheduleWithStops(id, trainNumber, trainTypeName, categoryName, stationCodes);
        // Assign commercial tracks to each ScheduleRow
        for (int i = 0; i < schedule.scheduleRows.size(); i++) {
            schedule.scheduleRows.get(i).commercialTrack = tracks.get(i);
        }
        return schedule;
    }
}
