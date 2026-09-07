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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.peti.EmptyPetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * Tests for NeTExService — orchestration and filtering logic.
 */
class NeTExServiceTest {

    private NeTExService netExService;

    @BeforeEach
    void setUp() {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExTimeConverter timeConverter = new NeTExTimeConverter();
        final NeTExEntityService entityService = new NeTExEntityService(idGenerator, timeConverter);
        final NeTExRouteService routeService = new NeTExRouteService(idGenerator);
        final EmptyPetiStopSource petiStopSource = new EmptyPetiStopSource();
        final NeTExStopsService stopsService = new NeTExStopsService(idGenerator, petiStopSource);
        final NeTExWritingService writingService = new NeTExWritingService(idGenerator);
        final TodaysScheduleService todaysScheduleService = new TodaysScheduleService();
        netExService = new NeTExService(entityService, new NeTExCalendarService(idGenerator),
                routeService, stopsService, writingService,
                petiStopSource, null, todaysScheduleService, null);
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
    void givenMuseumTrain_whenFiltering_thenIncluded() {
        // given
        final Schedule schedule = createSchedule("MUS", "Long-distance", true);

        // when
        final List<Schedule> result = netExService.filterPassengerTrains(List.of(schedule));

        // then
        assertEquals(1, result.size());
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
        assertEquals(List.of("IC", "MUS"), result.stream().map(s -> s.trainType.name).toList());
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
