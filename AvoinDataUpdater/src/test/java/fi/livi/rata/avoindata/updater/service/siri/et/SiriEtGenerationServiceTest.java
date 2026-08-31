package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExTimeConverter;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

class SiriEtGenerationServiceTest {

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    private ScheduleProviderService scheduleProviderService;
    private StationRepository stationRepository;
    private GTFSTrainRepository gtfsTrainRepository;
    private PetiStopSource petiStopSource;
    private GeneratedExportRepository generatedExportRepository;
    private NeTExService neTExService;
    private NeTExEntityService neTExEntityService;
    private NeTExIdGenerator neTExIdGenerator;
    private SiriWritingService siriWritingService;

    private SiriEtGenerationService service;

    @BeforeEach
    void setUp() throws Exception {
        scheduleProviderService = mock(ScheduleProviderService.class);
        stationRepository = mock(StationRepository.class);
        gtfsTrainRepository = mock(GTFSTrainRepository.class);
        petiStopSource = mock(PetiStopSource.class);
        generatedExportRepository = mock(GeneratedExportRepository.class);
        neTExService = mock(NeTExService.class);
        neTExIdGenerator = new NeTExIdGenerator();
        neTExEntityService = new NeTExEntityService(neTExIdGenerator, mock(NeTExTimeConverter.class));
        siriWritingService = new SiriWritingService();

        service = new SiriEtGenerationService(
                scheduleProviderService,
                stationRepository,
                gtfsTrainRepository,
                petiStopSource,
                neTExService,
                neTExEntityService,
                neTExIdGenerator,
                siriWritingService,
                generatedExportRepository
        );
    }

    // ===== HELPERS =====

    private void setupHappyPath() throws Exception {
        final Schedule schedule59 = createSchedule(59L, 12345L, Train.TimetableType.REGULAR, "IC");
        final List<Schedule> adhocSchedules = List.of();
        final List<Schedule> regularSchedules = List.of(schedule59);

        when(scheduleProviderService.getAdhocSchedules(any(LocalDate.class))).thenReturn(adhocSchedules);
        when(scheduleProviderService.getRegularSchedules(any(LocalDate.class))).thenReturn(regularSchedules);
        when(neTExService.resolveWinningSchedules(any(), any(), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Map.of(new TrainId(59L, TODAY), schedule59));

        final List<Station> stations = List.of(
                createStation("HKI", 1),
                createStation("TPE", 160),
                createStation("OL", 280)
        );
        when(stationRepository.findAll()).thenReturn(stations);

        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null, null, null)))
        ));
        when(petiStopSource.getMatcher()).thenReturn(matcher);

        final GTFSTrain train59 = createTrain(59L, TODAY);
        addStop(train59, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train59, "TPE",
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI), "1");
        addStop(train59, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        when(gtfsTrainRepository.findBySourceVersionGreaterThan(0L)).thenReturn(List.of(train59));
    }

    private void setupHappyPathTwoTrains() throws Exception {
        final Schedule schedule59 = createSchedule(59L, 12345L, Train.TimetableType.REGULAR, "IC");
        final List<Schedule> adhocSchedules = List.of();
        final List<Schedule> regularSchedules = List.of(schedule59);

        when(scheduleProviderService.getAdhocSchedules(any(LocalDate.class))).thenReturn(adhocSchedules);
        when(scheduleProviderService.getRegularSchedules(any(LocalDate.class))).thenReturn(regularSchedules);
        when(neTExService.resolveWinningSchedules(any(), any(), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Map.of(new TrainId(59L, TODAY), schedule59));

        final List<Station> stations = List.of(
                createStation("HKI", 1),
                createStation("TPE", 160),
                createStation("OL", 280)
        );
        when(stationRepository.findAll()).thenReturn(stations);

        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null, null, null)))
        ));
        when(petiStopSource.getMatcher()).thenReturn(matcher);

        final GTFSTrain train59 = createTrain(59L, TODAY);
        addStop(train59, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train59, "TPE",
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI), "1");
        addStop(train59, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        final GTFSTrain train999 = createTrain(999L, TODAY);
        addStop(train999, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, HELSINKI), "7");
        addStop(train999, "OL",
                ZonedDateTime.of(2026, 7, 15, 16, 0, 0, 0, HELSINKI), null, "1");

        when(gtfsTrainRepository.findBySourceVersionGreaterThan(0L)).thenReturn(List.of(train59, train999));
    }

    private static GTFSTrain createTrain(final long trainNumber, final LocalDate departureDate) {
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(trainNumber, departureDate);
        train.cancelled = false;
        train.timeTableRows = new ArrayList<>();
        return train;
    }

    private static void addStop(final GTFSTrain train, final String stationShortCode,
                                final ZonedDateTime arrivalTime, final ZonedDateTime departureTime,
                                final String track) {
        if (arrivalTime != null) {
            final GTFSTimeTableRow row = new GTFSTimeTableRow();
            row.stationShortCode = stationShortCode;
            row.type = TimeTableRow.TimeTableRowType.ARRIVAL;
            row.scheduledTime = arrivalTime;
            row.commercialStop = true;
            row.commercialTrack = track;
            row.train = train;
            train.timeTableRows.add(row);
        }
        if (departureTime != null) {
            final GTFSTimeTableRow row = new GTFSTimeTableRow();
            row.stationShortCode = stationShortCode;
            row.type = TimeTableRow.TimeTableRowType.DEPARTURE;
            row.scheduledTime = departureTime;
            row.commercialStop = true;
            row.commercialTrack = track;
            row.train = train;
            train.timeTableRows.add(row);
        }
    }

    private static Schedule createSchedule(final long trainNumber, final long id,
                                           final Train.TimetableType timetableType,
                                           final String typeName) {
        final Schedule schedule = new Schedule();
        schedule.trainNumber = trainNumber;
        schedule.id = id;
        schedule.timetableType = timetableType;
        schedule.startDate = LocalDate.of(2026, 1, 1);
        schedule.trainType = new fi.livi.rata.avoindata.common.domain.localization.TrainType(typeName);
        schedule.commuterLineId = null;
        return schedule;
    }

    private static Station createStation(final String shortCode, final int uicCode) {
        final Station station = new Station();
        station.shortCode = shortCode;
        station.uicCode = uicCode;
        station.passengerTraffic = true;
        return station;
    }

    @SuppressWarnings("unchecked")
    private List<GeneratedExport> capturePersistedExports() {
        final ArgumentCaptor<Collection<GeneratedExport>> captor =
                ArgumentCaptor.forClass(Collection.class);
        verify(generatedExportRepository).persist(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    // ===== GEN-01: Happy path — persists GeneratedExport with fileName "siri-et.xml" =====

    @Test
    void givenHappyPath_whenGenerate_thenPersistsWithCorrectFileName() throws Exception {
        // given
        setupHappyPath();

        // when
        service.generate();

        // then
        final List<GeneratedExport> exports = capturePersistedExports();
        assertEquals(1, exports.size());
        assertEquals("siri-et.xml", exports.get(0).fileName);
    }

    // ===== GEN-02: Happy path — persisted bytes are schema-valid SIRI-ET XML =====

    @Test
    void givenHappyPath_whenGenerate_thenPersistedBytesAreValidSiriXml() throws Exception {
        // given
        setupHappyPath();

        // when
        service.generate();

        // then
        final List<GeneratedExport> exports = capturePersistedExports();
        assertNotNull(exports.get(0).data);
        assertTrue(exports.get(0).data.length > 0);
        assertTrue(siriWritingService.isSchemaValid(exports.get(0).data));
    }

    // ===== GEN-03: Happy path — persisted doc contains EstimatedVehicleJourney for train 59 =====

    @Test
    void givenHappyPath_whenGenerate_thenDocContainsEvjForTrain59() throws Exception {
        // given
        setupHappyPath();

        // when
        service.generate();

        // then
        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.get(0).data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"),
                "Expected ServiceJourney id for train 59 in XML");
    }

    // ===== GEN-04: Schedule map built from getAdhocSchedules + getRegularSchedules via getDaysSchedules =====

    @Test
    void givenSchedules_whenGenerate_thenResolveWinningSchedulesCalledWithBothLists() throws Exception {
        // given
        final Schedule scheduleA = createSchedule(59L, 111L, Train.TimetableType.ADHOC, "IC");
        scheduleA.startDate = TODAY;
        final Schedule scheduleB = createSchedule(60L, 222L, Train.TimetableType.REGULAR, "S");

        when(scheduleProviderService.getAdhocSchedules(any(LocalDate.class))).thenReturn(List.of(scheduleA));
        when(scheduleProviderService.getRegularSchedules(any(LocalDate.class))).thenReturn(List.of(scheduleB));
        when(neTExService.resolveWinningSchedules(any(), any(), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Map.of(new TrainId(59L, TODAY), scheduleA));
        when(stationRepository.findAll()).thenReturn(List.of());
        when(petiStopSource.getMatcher()).thenReturn(new PetiUicMatcher(List.of()));
        when(gtfsTrainRepository.findBySourceVersionGreaterThan(0L)).thenReturn(List.of());

        // when
        service.generate();

        // then
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Schedule>> adhocCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Schedule>> regularCaptor = ArgumentCaptor.forClass(List.class);
        verify(neTExService).resolveWinningSchedules(adhocCaptor.capture(), regularCaptor.capture(),
                any(), any(LocalDate.class), any(LocalDate.class));
        assertEquals(List.of(scheduleA), adhocCaptor.getValue());
        assertEquals(List.of(scheduleB), regularCaptor.getValue());
    }

    // ===== GEN-05: StationUicLookup built from StationRepository.findAll() =====

    @Test
    void givenStations_whenGenerate_thenStationRepositoryFindAllCalled() throws Exception {
        // given
        setupHappyPath();

        // when
        service.generate();

        // then
        verify(stationRepository).findAll();
    }

    // ===== GEN-06: Prebuilt PetiUicMatcher is obtained once per cycle =====

    @Test
    void givenPetiStopSource_whenGenerate_thenGetMatcherCalledExactlyOnce() throws Exception {
        // given
        setupHappyPath();

        // when
        service.generate();

        // then
        verify(petiStopSource, times(1)).getMatcher();
    }

    // ===== GEN-07: Train with no matching schedule is skipped =====

    @Test
    void givenTrainWithNoSchedule_whenGenerate_thenOnlyMatchedTrainInOutput() throws Exception {
        // given
        setupHappyPathTwoTrains();

        // when
        service.generate();

        // then
        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.get(0).data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"),
                "Expected train 59 in output");
        assertFalse(xml.contains("999"),
                "Train 999 should not appear (no schedule match)");
    }

    // ===== GEN-08: ScheduleProviderService throws ExecutionException → no persist =====

    @Test
    void givenExecutionException_whenGenerate_thenNoPersistAndNoThrow() throws Exception {
        // given
        when(scheduleProviderService.getAdhocSchedules(any(LocalDate.class)))
                .thenThrow(new ExecutionException("fail", new RuntimeException("cause")));

        // when / then
        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-09: ScheduleProviderService throws InterruptedException → no persist =====

    @Test
    void givenInterruptedException_whenGenerate_thenNoPersistAndNoThrow() throws Exception {
        // given
        when(scheduleProviderService.getAdhocSchedules(any(LocalDate.class))).thenReturn(List.of());
        when(scheduleProviderService.getRegularSchedules(any(LocalDate.class)))
                .thenThrow(new InterruptedException("interrupted"));

        // when / then
        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-10: StationRepository throws RuntimeException → no persist =====

    @Test
    void givenStationRepoException_whenGenerate_thenNoPersistAndNoThrow() throws Exception {
        // given
        when(scheduleProviderService.getAdhocSchedules(any(LocalDate.class))).thenReturn(List.of());
        when(scheduleProviderService.getRegularSchedules(any(LocalDate.class))).thenReturn(List.of());
        when(neTExService.resolveWinningSchedules(any(), any(), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Map.of());
        when(stationRepository.findAll()).thenThrow(new RuntimeException("DB down"));

        // when / then
        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }
}
