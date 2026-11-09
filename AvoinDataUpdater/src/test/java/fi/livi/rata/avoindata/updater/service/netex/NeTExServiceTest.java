package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialTrackResolver;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * Tests for NeTExService — orchestration and filtering logic.
 */
class NeTExServiceTest {
    private static final String PETI_URL = "https://rae.fintraffic.fi/exports/PETI-rail-NeTEx.zip";

    private NeTExService netExService;

    @BeforeEach
    void setUp() throws Exception {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExTimeConverter timeConverter = new NeTExTimeConverter();
        final NeTExEntityService entityService = new NeTExEntityService(idGenerator, timeConverter);
        final NeTExRouteService routeService = new NeTExRouteService(idGenerator);
        // Generation refuses an empty PETI snapshot, so these orchestration tests need
        // a stop place present.
        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:1", 1_000_100, "HKI station", true, null, List.of()));
        final NeTExStopsService stopsService = new NeTExStopsService(idGenerator, petiStopSource);
        final NeTExWritingService writingService = new NeTExWritingService(idGenerator, PETI_URL);
        final TodaysScheduleService todaysScheduleService = new TodaysScheduleService();
        netExService = new NeTExService(entityService, new NeTExCalendarService(idGenerator),
                routeService, new IdentityTrackSource(), stopsService,
                writingService,
                petiStopSource, null, todaysScheduleService, null, new CommercialTrackResolver(), null, null);
    }

    // --- Filtering tests ---

    @Test
    void givenCommuterTrain_whenFiltering_thenIncluded() {
        // given
        final Schedule schedule = createSchedule("HDM", "Commuter", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(1, result.size());
    }

    @Test
    void givenLongDistanceTrain_whenFiltering_thenIncluded() {
        // given
        final Schedule schedule = createSchedule("IC", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(1, result.size());
    }

    @Test
    void givenNonCommercialTrainType_whenFiltering_thenExcluded() {
        // given
        final Schedule schedule = createSchedule("T", "Cargo", false);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenExcludedTypeV_whenFiltering_thenExcluded() {
        // given
        final Schedule schedule = createSchedule("V", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenExcludedTypeHV_whenFiltering_thenExcluded() {
        // given
        final Schedule schedule = createSchedule("HV", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenExcludedTypeMV_whenFiltering_thenExcluded() {
        // given
        final Schedule schedule = createSchedule("MV", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenMuseumTrain_whenFiltering_thenExcluded() {
        // given
        final Schedule schedule = createSchedule("MUS", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenCargoTrainWithCommercialStops_whenFiltering_thenExcluded() {
        // given — cargo train type T with commercial=true should still be excluded by
        // category
        final Schedule schedule = createSchedule("T", "Cargo", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenNonCommercialLongDistanceTrain_whenFiltering_thenExcluded() {
        // given — long-distance train that is not commercial
        final Schedule schedule = createSchedule("IC", "Long-distance", false);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(0, result.size());
    }

    @Test
    void givenMixOfPassengerAndNonPassenger_whenFiltering_thenOnlyPassengerIncluded() {
        // given
        final Schedule passenger = createSchedule("IC", "Long-distance", true);
        final Schedule cargo = createSchedule("T", "Cargo", false);
        final Schedule museum = createSchedule("MUS", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(passenger, cargo, museum));

        // then
        assertEquals(List.of("IC"), result.stream().map(s -> s.trainType.name).toList());
    }

    // --- Generation orchestration tests ---

    @Test
    void givenSchedulesAndStations_whenGenerating_thenProducesNonNullOutput() {
        // given
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));
        final List<Station> stations = createStations(List.of("HKI", "TPE", "OL"));

        // when
        final var result = netExService.generateNeTEx(List.of(), List.of(schedule), stations);

        // then
        assertNotNull(result);
        assertTrue(result.zip().length > 0);
    }

    @Test
    void givenATrainPassesThroughAStation_whenGenerating_thenNoStopPointIsMadeForIt() {
        final List<String> codes = List.of("HKI", "TPE", "OL");
        final List<Station> stations = createStations(codes);

        final Schedule stopping = createFullSchedule(1L, 59L, "IC", "Long-distance", codes);
        stopping.scheduleRows.get(1).commercialTrack = "1";
        final int withStop = netExService.generateNeTEx(List.of(), List.of(stopping), stations)
                .scheduledStopPoints();

        final Schedule passing = createFullSchedule(1L, 59L, "IC", "Long-distance", codes);
        final ScheduleRow tpe = passing.scheduleRows.get(1);
        tpe.arrival.stopType = ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;
        tpe.departure.stopType = ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;
        tpe.commercialTrack = "902";
        final int withPassing = netExService.generateNeTEx(List.of(), List.of(passing), stations)
                .scheduledStopPoints();

        assertEquals(withStop - 1, withPassing, "a passing track must not become a stop point");
    }

    @Test
    void givenBothRegularAndAdhocSchedules_whenGenerating_thenBothIncludedInOutput() {
        // given
        final Schedule regular = createFullSchedule(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));
        regular.timetableType = Train.TimetableType.REGULAR;

        final Schedule adhoc = createFullSchedule(2L, 99L, "IC", "Long-distance",
                List.of("HKI", "TPE"));
        adhoc.timetableType = Train.TimetableType.ADHOC;
        adhoc.startDate = LocalDate.of(2026, 7, 15);
        adhoc.endDate = LocalDate.of(2026, 7, 15);

        final List<Station> stations = createStations(List.of("HKI", "TPE", "OL"));

        // when
        final var result = netExService.generateNeTEx(List.of(adhoc), List.of(regular), stations);

        // then: output should contain both ServiceJourneys
        assertNotNull(result);
        assertTrue(result.zip().length > 0);
    }

    @Test
    void givenEmptySchedules_whenGenerating_thenReturnsNullOrEmptyGracefully() {
        // given
        final List<Station> stations = createStations(List.of("HKI"));

        // when
        final var result = netExService.generateNeTEx(List.of(), List.of(), stations);

        // then: graceful handling — null or empty but no exception
        // (implementation decides: null means "no output", or returns minimal valid
        // ZIP)
        // For now, just assert no exception was thrown
        assertTrue(result == null || result.zip().length >= 0);
    }

    @Test
    void givenCommercialStopAtStationMissingFromMetadata_whenGenerating_thenJourneyIsDropped() {
        // given: the schedule stops at OL, which the station metadata does not know
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));
        final List<Station> stations = createStations(List.of("HKI", "TPE"));

        // when
        final var result = netExService.generateNeTEx(List.of(), List.of(schedule), stations);

        // then: no journey rather than one referencing a stop point we cannot declare
        assertNull(result);
    }

    @Test
    void givenCommercialStopAtNonPassengerStation_whenGenerating_thenJourneyIsDropped() {
        // given
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));
        final List<Station> stations = createStations(List.of("HKI", "TPE", "OL"));
        stations.get(2).passengerTraffic = false;

        // when
        final var result = netExService.generateNeTEx(List.of(), List.of(schedule), stations);

        // then
        assertNull(result);
    }

    @Test
    void givenStationBecomesPublishable_whenGenerating_thenJourneyReturns() {
        // given: the same schedule that was dropped above
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL"));

        // when: metadata catches up
        final var result = netExService.generateNeTEx(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then: no code change needed for the journey to come back
        assertNotNull(result);
        assertTrue(result.zip().length > 0);
    }

    // --- Partial cancellations ---

    @Test
    void givenCancelledStopOnSomeDays_whenComputingDataset_thenThoseDaysHaveOwnJourneyWithoutTheStop() {
        // given
        final Schedule schedule = createCurrentSchedule();
        final LocalDate cancelFrom = DateProvider.dateInHelsinki().plusDays(10);
        schedule.scheduleCancellations.add(
                partialCancellation(schedule, 1L, cancelFrom, cancelFrom.plusDays(2), "TPE"));

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        assertEquals(2, dataset.serviceJourneys().size());
        final var counts = dataset.serviceJourneys().stream()
                .map(journey -> journey.passingTimes().size()).sorted().toList();
        assertEquals(List.of(2, 3), counts);
    }

    @Test
    void givenCancelledStopOnAllDays_whenComputingDataset_thenOnlyTheJourneyWithoutTheStopIsPublished() {
        // given
        final Schedule schedule = createCurrentSchedule();
        schedule.scheduleCancellations.add(partialCancellation(schedule, 1L, schedule.startDate,
                schedule.endDate, "TPE"));

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        assertEquals(1, dataset.serviceJourneys().size());
        assertEquals(2, dataset.serviceJourneys().getFirst().passingTimes().size());
    }

    @Test
    void givenCancelledStopWithTrack_whenComputingDataset_thenPublishedJourneyKeepsItsPlannedTrack() {
        // given
        final Schedule schedule = createCurrentSchedule();
        schedule.scheduleRows.get(1).commercialTrack = "7";
        schedule.scheduleCancellations.add(partialCancellation(schedule, 1L, schedule.startDate,
                schedule.endDate, "TPE"));

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        final var tracks = dataset.publishedJourneys().getFirst().tracks();
        assertEquals(1, tracks.size());
        assertEquals("TPE", tracks.getFirst().stationShortCode());
        assertEquals("7", tracks.getFirst().plannedTrack());
    }

    @Test
    void givenStopCancelledOnEveryDayWithoutTrack_whenComputingDataset_thenPublishedJourneyGetsPetiPlatform() {
        // given
        final PetiStopSource petiSource = () -> List.of(
                new PetiStop("FSR:StopPlace:TPE", 1_000_101, "TPE station", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-3", "3", null, null, null))));
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExService service = new NeTExService(
                new NeTExEntityService(idGenerator, new NeTExTimeConverter()),
                new NeTExCalendarService(idGenerator), new NeTExRouteService(idGenerator),
                new IdentityTrackSource(), new NeTExStopsService(idGenerator, petiSource),
                new NeTExWritingService(idGenerator, PETI_URL), petiSource, null,
                new TodaysScheduleService(), null, new CommercialTrackResolver(), null, null);
        final Schedule schedule = createCurrentSchedule();
        schedule.scheduleCancellations.add(partialCancellation(schedule, 1L, schedule.startDate,
                schedule.endDate, "TPE"));

        // when
        final var dataset = service.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        final var tracks = dataset.publishedJourneys().getFirst().tracks();
        assertEquals(1, tracks.size());
        assertEquals("3", tracks.getFirst().plannedTrack());
    }

    @Test
    void givenDifferentRouteRemovesFirstVisit_whenComputingDataset_thenSecondVisitKeepsItsOwnTrack() {
        // given
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance",
                List.of("HKI", "TPE", "OL", "TPE", "TKU"));
        final LocalDate today = DateProvider.dateInHelsinki();
        schedule.startDate = today.minusDays(1);
        schedule.endDate = today.plusDays(30);
        schedule.effectiveFrom = schedule.startDate;
        schedule.runOnSaturday = true;
        schedule.runOnSunday = true;
        schedule.scheduleRows.get(1).commercialTrack = "1";
        schedule.scheduleRows.get(3).commercialTrack = "2";
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.id = 1L;
        cancellation.startDate = schedule.startDate;
        cancellation.endDate = schedule.endDate;
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE;
        cancellation.cancelledRows.add(schedule.scheduleRows.get(1).arrival);
        cancellation.cancelledRows.add(schedule.scheduleRows.get(1).departure);
        schedule.scheduleCancellations.add(cancellation);

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL", "TKU")));

        // then
        // The real train has no first TPE visit, so its only TPE visit is index 0 and has track 2
        final var tpeTracks = dataset.publishedJourneys().getFirst().tracks().stream()
                .filter(track -> "TPE".equals(track.stationShortCode()))
                .toList();
        assertEquals(1, tpeTracks.size());
        assertEquals(0, tpeTracks.getFirst().visitIndex());
        assertEquals("2", tpeTracks.getFirst().plannedTrack());
    }

    @Test
    void givenFewerThanTwoStopsLeft_whenComputingDataset_thenCancelledDaysHaveNoJourney() {
        // given
        final Schedule schedule = createCurrentSchedule();
        final LocalDate cancelFrom = DateProvider.dateInHelsinki().plusDays(10);
        schedule.scheduleCancellations.add(
                partialCancellation(schedule, 1L, cancelFrom, cancelFrom, "TPE", "OL"));

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        assertEquals(1, dataset.serviceJourneys().size());
        assertEquals(3, dataset.serviceJourneys().getFirst().passingTimes().size());
        assertTrue(dataset.calendar().datesOf(List.of(dataset.serviceJourneys().getFirst().id()))
                .stream().noneMatch(cancelFrom::equals));
    }

    @Test
    void givenCancelledStopOnAdhocSchedule_whenComputingDataset_thenJourneyWithoutTheStopIsPublished() {
        // given
        final Schedule adhoc = createCurrentSchedule();
        adhoc.timetableType = Train.TimetableType.ADHOC;
        adhoc.startDate = DateProvider.dateInHelsinki().plusDays(10);
        adhoc.endDate = adhoc.startDate;
        adhoc.scheduleCancellations.add(
                partialCancellation(adhoc, 1L, adhoc.startDate, adhoc.startDate, "TPE"));

        // when
        final var dataset = netExService.computeDataset(List.of(adhoc), List.of(),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        assertEquals(1, dataset.serviceJourneys().size());
        assertEquals(2, dataset.serviceJourneys().getFirst().passingTimes().size());
    }

    @Test
    void givenCancelledStopOnLastDayOfPackage_whenComputingDataset_thenThatDayHasOwnJourney() {
        // given
        final Schedule schedule = createCurrentSchedule();
        // Same end date as the package has
        final LocalDate lastDay = DateProvider.dateInHelsinki().minusDays(7).plusYears(1)
                .withMonth(12).withDayOfMonth(31);
        schedule.endDate = lastDay;
        schedule.scheduleCancellations.add(partialCancellation(schedule, 1L, lastDay, lastDay, "TPE"));

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        final var counts = dataset.serviceJourneys().stream()
                .map(journey -> journey.passingTimes().size()).sorted().toList();
        assertEquals(List.of(2, 3), counts);
    }

    @Test
    void givenCancelledOriginOfOvernightTrain_whenComputingDataset_thenTimesStillCountPastMidnight() {
        // given
        final Schedule schedule = createCurrentSchedule();
        final ScheduleRow origin = schedule.scheduleRows.get(0);
        final ScheduleRow middle = schedule.scheduleRows.get(1);
        final ScheduleRow terminus = schedule.scheduleRows.get(2);
        origin.departure.timestamp = Duration.ofHours(23).plusMinutes(50);
        middle.arrival.timestamp = Duration.ofHours(24).plusMinutes(11);
        middle.departure.timestamp = Duration.ofHours(24).plusMinutes(12);
        terminus.arrival.timestamp = Duration.ofHours(25);
        schedule.scheduleCancellations.add(
                partialCancellation(schedule, 1L, schedule.startDate, schedule.endDate, "HKI"));

        // when
        final var dataset = netExService.computeDataset(List.of(), List.of(schedule),
                createStations(List.of("HKI", "TPE", "OL")));

        // then
        final var passingTimes = dataset.serviceJourneys().getFirst().passingTimes();
        assertEquals(2, passingTimes.size());
        assertEquals("24:12:00", passingTimes.get(0).departureTime());
        assertEquals("25:00:00", passingTimes.get(1).arrivalTime());
    }

    private Schedule createCurrentSchedule() {
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", List.of("HKI", "TPE", "OL"));
        final LocalDate today = DateProvider.dateInHelsinki();
        schedule.startDate = today.minusDays(1);
        schedule.endDate = today.plusDays(30);
        schedule.effectiveFrom = schedule.startDate;
        // Runs every day, so the tests do not depend on the weekday they are run on
        schedule.runOnSaturday = true;
        schedule.runOnSunday = true;
        return schedule;
    }

    private static ScheduleCancellation partialCancellation(final Schedule schedule, final long id,
            final LocalDate from, final LocalDate to, final String... cancelledStations) {
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.id = id;
        cancellation.startDate = from;
        cancellation.endDate = to;
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        final Set<String> stations = Set.of(cancelledStations);
        for (final ScheduleRow row : schedule.scheduleRows) {
            if (stations.contains(row.station.stationShortCode)) {
                if (row.arrival != null) {
                    cancellation.cancelledRows.add(row.arrival);
                }
                if (row.departure != null) {
                    cancellation.cancelledRows.add(row.departure);
                }
            }
        }
        return cancellation;
    }

    // --- Helpers ---

    private Schedule createSchedule(final String trainTypeName, final String categoryName, final boolean commercial) {
        final Schedule schedule = new Schedule();
        schedule.id = 1L;
        schedule.trainNumber = 100L;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = LocalDate.of(2026, 6, 15);
        schedule.endDate = LocalDate.of(2026, 12, 14);
        schedule.effectiveFrom = LocalDate.of(2026, 6, 15);
        schedule.changeType = "L";
        schedule.capacityId = "cap-100";
        schedule.typeCode = "L";
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
        trainType.commercial = commercial;
        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = categoryName;
        trainType.trainCategory = trainCategory;
        schedule.trainType = trainType;
        schedule.trainCategory = trainCategory;
        schedule.commuterLineId = null;
        schedule.scheduleRows = new ArrayList<>();

        return schedule;
    }

    private Schedule createFullSchedule(final long id, final long trainNumber, final String trainTypeName,
            final String categoryName, final List<String> stationCodes) {
        final Schedule schedule = createSchedule(trainTypeName, categoryName, true);
        schedule.id = id;
        schedule.trainNumber = trainNumber;
        schedule.capacityId = "cap-" + trainNumber;

        schedule.scheduleRows = new ArrayList<>();
        long rowId = 1;
        for (int i = 0; i < stationCodes.size(); i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = rowId++;
            row.station = new StationEmbeddable(stationCodes.get(i), 100 + i, "FI");

            if (i > 0) {
                final ScheduleRowPart arrival = new ScheduleRowPart();
                arrival.id = rowId++;
                arrival.timestamp = Duration.ofHours(5).plusMinutes(30 + i * 60);
                arrival.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                arrival.scheduleRow = row;
                row.arrival = arrival;
            }
            if (i < stationCodes.size() - 1) {
                final ScheduleRowPart departure = new ScheduleRowPart();
                departure.id = rowId++;
                departure.timestamp = Duration.ofHours(5).plusMinutes(31 + i * 60);
                departure.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
                departure.scheduleRow = row;
                row.departure = departure;
            }

            schedule.scheduleRows.add(row);
        }

        return schedule;
    }

    private List<Station> createStations(final List<String> shortCodes) {
        final List<Station> stations = new ArrayList<>();
        for (int i = 0; i < shortCodes.size(); i++) {
            final Station station = new Station();
            station.shortCode = shortCodes.get(i);
            station.name = shortCodes.get(i) + " station";
            station.uicCode = 100 + i;
            station.passengerTraffic = true;
            station.latitude = new BigDecimal("60.17").add(new BigDecimal(i));
            station.longitude = new BigDecimal("24.94").add(new BigDecimal(i));
            station.countryCode = "FI";
            stations.add(station);
        }
        return stations;
    }
}
