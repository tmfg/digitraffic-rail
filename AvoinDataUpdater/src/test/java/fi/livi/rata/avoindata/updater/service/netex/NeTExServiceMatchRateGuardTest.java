package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.peti.EmptyPetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for NeTExService match-rate guard logic.
 */
class NeTExServiceMatchRateGuardTest {

    @Test
    void givenEmptyPetiSource_whenGenerating_thenGuardDoesNotThrow() {
        // given — empty PETI source, total == 0 → guard is skipped
        final NeTExService service = createServiceWithPetiSource(new EmptyPetiStopSource(), 0.95);
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", List.of("HKI", "TPE"));
        final List<Station> stations = createStations(List.of("HKI", "TPE"));

        // when/then — no exception
        assertDoesNotThrow(() -> service.generateNeTEx(List.of(), List.of(schedule), stations));
    }

    @Test
    void givenPetiSourceThatFailsToLoad_whenGenerating_thenPropagates() {
        // given — a live feed that cannot supply data fails in ensureLoaded(); generation must not swallow it
        final PetiStopSource failing = new PetiStopSource() {
            @Override
            public List<PetiStop> getStops() {
                return List.of();
            }

            @Override
            public void ensureLoaded() {
                throw new IllegalStateException("PETI stop source returned no stops");
            }
        };
        final NeTExService service = createServiceWithPetiSource(failing, 0.95);
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", List.of("HKI", "TPE"));
        final List<Station> stations = createStations(List.of("HKI", "TPE"));

        // when/then
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generateNeTEx(List.of(), List.of(schedule), stations));
        assertTrue(ex.getMessage().contains("PETI"), "Message should mention PETI");
    }

    @Test
    void givenMatchRateAboveThreshold_whenGenerating_thenGuardDoesNotThrow() {
        // given — 2 stations, both matched (rate 1.0 > 0.95)
        final List<PetiStop> petiStops = List.of(
                new PetiStop("FSR:StopPlace:1", 1_000_100, "HKI station", true, null, List.of()),
                new PetiStop("FSR:StopPlace:2", 1_000_101, "TPE station", true, null, List.of()));
        final NeTExService service = createServiceWithPetiSource(() -> petiStops, 0.95);
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", List.of("HKI", "TPE"));
        final List<Station> stations = createStations(List.of("HKI", "TPE"));

        // when/then — no exception (rate 1.0 >= 0.95)
        assertDoesNotThrow(() -> service.generateNeTEx(List.of(), List.of(schedule), stations));
    }

    @Test
    void givenMatchRateExactlyAtThreshold_whenGenerating_thenGuardDoesNotThrow() {
        // given — create scenario where rate == threshold exactly
        // 19 matched out of 20 total = 0.95 exactly, threshold 0.95 → passes (rate < threshold is the fail condition)
        final List<PetiStop> petiStops = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            petiStops.add(new PetiStop("FSR:StopPlace:" + i, 1_000_100 + i, "Station " + i, true, null, List.of()));
        }
        final NeTExService service = createServiceWithPetiSource(() -> petiStops, 0.95);

        final List<String> codes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            codes.add("S" + String.format("%02d", i));
        }
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", codes);
        final List<Station> stations = createStationsWithUic(codes, 100); // UIC 100..119, 19 of 20 match

        // when/then — rate = 19/20 = 0.95, not < 0.95 → does not throw
        assertDoesNotThrow(() -> service.generateNeTEx(List.of(), List.of(schedule), stations));
    }

    @Test
    void givenMatchRateBelowThreshold_whenGenerating_thenGuardThrowsIllegalStateException() {
        // given — 1 matched, 1 unmatched (rate = 0.5 < 0.95)
        final List<PetiStop> petiStops = List.of(
                new PetiStop("FSR:StopPlace:1", 1_000_100, "HKI station", true, null, List.of()));
        final NeTExService service = createServiceWithPetiSource(() -> petiStops, 0.95);
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", List.of("HKI", "TPE"));
        final List<Station> stations = createStations(List.of("HKI", "TPE"));

        // when/then — rate 0.5 < 0.95 → throws
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generateNeTEx(List.of(), List.of(schedule), stations));
        assertTrue(ex.getMessage().contains("0.50") || ex.getMessage().contains("0,50"),
                "Message should contain actual rate");
        assertTrue(ex.getMessage().contains("0.95") || ex.getMessage().contains("0,95"),
                "Message should contain threshold");
    }

    @Test
    void givenTenStationsEightMatched_thenCountsAreCorrect() {
        // given — 10 passenger stations, 8 matched by PETI
        final List<PetiStop> petiStops = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            petiStops.add(new PetiStop("FSR:StopPlace:" + i, 1_000_100 + i, "Station " + i, true, null, List.of()));
        }
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExStopsService stopsService = new NeTExStopsService(idGenerator, () -> petiStops);

        final List<Station> stations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final Station s = new Station();
            s.shortCode = "S" + i;
            s.name = "Station " + i;
            s.uicCode = 100 + i;
            s.passengerTraffic = true;
            s.latitude = new BigDecimal("60.17");
            s.longitude = new BigDecimal("24.94");
            s.countryCode = "FI";
            stations.add(s);
        }

        // when
        final NeTExStopsData stopsData = stopsService.createStopsData(stations, nullTrackPairs(stations));

        // then
        assertEquals(8, stopsData.matchedCount());
        assertEquals(2, stopsData.unmatchedCount());
        assertEquals(10, stopsData.matchedCount() + stopsData.unmatchedCount());
        final double matchRate = (double) stopsData.matchedCount() /
                (stopsData.matchedCount() + stopsData.unmatchedCount());
        assertEquals(0.8, matchRate, 0.001);
    }

    // --- B5: Telemetry field names use stop-assignment unit ---

    @Test
    void givenSuccessfulGeneration_whenLogEmitted_thenFieldNamesUseStopAssignmentUnit() throws Exception {
        // given — wire a full NeTExService with mocked schedule/station/export repos
        final List<PetiStop> petiStops = List.of(
                new PetiStop("FSR:StopPlace:1", 1_000_100, "HKI station", true, null, List.of()),
                new PetiStop("FSR:StopPlace:2", 1_000_101, "TPE station", true, null, List.of()));
        final NeTExService service = createServiceWithMockedRepos(() -> petiStops, 0.0,
                List.of("HKI", "TPE"));

        // given — attach Logback ListAppender to capture log output
        final Logger logbackLogger = (Logger) LoggerFactory.getLogger(NeTExService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            // when — trigger the no-arg generateNeTEx() which emits the wide event
            service.generateNeTEx();

            // then — find the rail.netex.generation event in captured log
            final String generationLog = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(msg -> msg.contains("event=rail.netex.generation") && msg.contains("outcome=success"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Expected rail.netex.generation success event in log, got: " + appender.list));

            // then — green phase renames fields from "peti_stations_*" to "peti_stop_assignments_*"
            // Currently the log uses peti_stations_total → this assertion FAILS in red phase
            assertTrue(generationLog.contains("peti_stop_assignments_total="),
                    "Log should contain 'peti_stop_assignments_total=' but was: " + generationLog);
            assertTrue(generationLog.contains("peti_stop_assignments_matched="),
                    "Log should contain 'peti_stop_assignments_matched=' but was: " + generationLog);
            assertTrue(generationLog.contains("peti_stop_assignments_unmatched="),
                    "Log should contain 'peti_stop_assignments_unmatched=' but was: " + generationLog);
            assertFalse(generationLog.contains("peti_stations_total="),
                    "Log should NOT contain legacy 'peti_stations_total=' but was: " + generationLog);
        } finally {
            logbackLogger.detachAppender(appender);
        }
    }

    // --- Helpers ---

    private static List<NeTExStopsService.StationTrackPair> nullTrackPairs(final List<Station> stations) {
        return stations.stream()
                .map(s -> new NeTExStopsService.StationTrackPair(s.shortCode, null))
                .toList();
    }

    private NeTExService createServiceWithPetiSource(final PetiStopSource petiSource, final double threshold) {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExTimeConverter timeConverter = new NeTExTimeConverter();
        final NeTExEntityService entityService = new NeTExEntityService(idGenerator, timeConverter);
        final NeTExCalendarService calendarService = new NeTExCalendarService(idGenerator);
        final NeTExRouteService routeService = new NeTExRouteService(idGenerator);
        final NeTExStopsService stopsService = new NeTExStopsService(idGenerator, petiSource);
        final NeTExWritingService writingService = new NeTExWritingService(idGenerator);
        final TodaysScheduleService todaysScheduleService = new TodaysScheduleService();
        final NeTExService service = new NeTExService(entityService, calendarService, routeService, stopsService,
                writingService, petiSource, null, todaysScheduleService, null, null);

        // Set minMatchRate via reflection (normally injected by @Value)
        try {
            final Field field = NeTExService.class.getDeclaredField("minMatchRate");
            field.setAccessible(true);
            field.setDouble(service, threshold);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to set minMatchRate", e);
        }

        return service;
    }

    private List<Station> createStations(final List<String> shortCodes) {
        return createStationsWithUic(shortCodes, 100);
    }

    private List<Station> createStationsWithUic(final List<String> shortCodes, final int startUic) {
        final List<Station> stations = new ArrayList<>();
        for (int i = 0; i < shortCodes.size(); i++) {
            final Station station = new Station();
            station.shortCode = shortCodes.get(i);
            station.name = shortCodes.get(i) + " station";
            station.uicCode = startUic + i;
            station.passengerTraffic = true;
            station.latitude = new BigDecimal("60.17").add(new BigDecimal(i));
            station.longitude = new BigDecimal("24.94").add(new BigDecimal(i));
            station.countryCode = "FI";
            stations.add(station);
        }
        return stations;
    }

    private Schedule createFullSchedule(final long id, final long trainNumber, final String trainTypeName,
            final String categoryName, final List<String> stationCodes) {
        final Schedule schedule = new Schedule();
        schedule.id = id;
        schedule.trainNumber = trainNumber;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = LocalDate.of(2026, 6, 15);
        schedule.endDate = LocalDate.of(2026, 12, 14);
        schedule.effectiveFrom = LocalDate.of(2026, 6, 15);
        schedule.changeType = "L";
        schedule.capacityId = "cap-" + trainNumber;
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
        trainType.commercial = true;
        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = categoryName;
        trainType.trainCategory = trainCategory;
        schedule.trainType = trainType;
        schedule.trainCategory = trainCategory;
        schedule.commuterLineId = null;

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

    /**
     * Creates a NeTExService wired with mocked ScheduleProviderService, StationRepository,
     * and GeneratedExportRepository — suitable for testing the no-arg generateNeTEx()
     * which emits the wide-event log.
     */
    @SuppressWarnings("unchecked")
    private NeTExService createServiceWithMockedRepos(final PetiStopSource petiSource,
            final double threshold, final List<String> stationCodes) throws Exception {
        final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
        final NeTExTimeConverter timeConverter = new NeTExTimeConverter();
        final NeTExEntityService entityService = new NeTExEntityService(idGenerator, timeConverter);
        final NeTExCalendarService calendarService = new NeTExCalendarService(idGenerator);
        final NeTExRouteService routeService = new NeTExRouteService(idGenerator);
        final NeTExStopsService stopsService = new NeTExStopsService(idGenerator, petiSource);
        final NeTExWritingService writingService = new NeTExWritingService(idGenerator);
        final TodaysScheduleService todaysScheduleService = new TodaysScheduleService();

        final ScheduleProviderService scheduleProviderService = mock(ScheduleProviderService.class);
        final StationRepository stationRepository = mock(StationRepository.class);
        final GeneratedExportRepository generatedExportRepository = mock(GeneratedExportRepository.class);

        // Set up schedule provider to return a single regular schedule spanning today
        final Schedule schedule = createFullSchedule(1L, 59L, "IC", "Long-distance", stationCodes);
        when(scheduleProviderService.getAdhocSchedules(any())).thenReturn(List.of());
        when(scheduleProviderService.getRegularSchedules(any())).thenReturn(List.of(schedule));

        // Set up station repository to return matching stations
        final List<Station> stations = createStations(stationCodes);
        when(stationRepository.findAll()).thenReturn(stations);

        final NeTExService service = new NeTExService(entityService, calendarService, routeService, stopsService,
                writingService, petiSource, scheduleProviderService, todaysScheduleService, stationRepository,
                generatedExportRepository);

        // Set minMatchRate via reflection
        final Field field = NeTExService.class.getDeclaredField("minMatchRate");
        field.setAccessible(true);
        field.setDouble(service, threshold);

        return service;
    }
}
