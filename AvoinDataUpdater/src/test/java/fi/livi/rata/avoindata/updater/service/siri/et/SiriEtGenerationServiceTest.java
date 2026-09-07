package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourneyTrack;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;

/**
 * SIRI-ET generation has a hard dependency on the NeTEx-published journey refs in the DB: it reads the
 * journey/line refs and planned tracks from {@link NeTExPublishedJourneyRepository} and fails the cycle — with
 * no live fallback — when they are missing, absent for the operating days, or stale.
 */
class SiriEtGenerationServiceTest {

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");
    // Trains are dated "today" (Helsinki) so they are current-operating-day journeys, never stale carryovers.
    private static final LocalDate TODAY = DateProvider.dateInHelsinki();
    private static final long DATASET_VERSION = 5L;

    private StationRepository stationRepository;
    private GTFSTrainRepository gtfsTrainRepository;
    private PetiStopSource petiStopSource;
    private GeneratedExportRepository generatedExportRepository;
    private SiriWritingService siriWritingService;
    private NeTExPublishedJourneyRepository publishedJourneyRepository;

    private SiriEtGenerationService service;

    @BeforeEach
    void setUp() {
        stationRepository = mock(StationRepository.class);
        gtfsTrainRepository = mock(GTFSTrainRepository.class);
        petiStopSource = mock(PetiStopSource.class);
        generatedExportRepository = mock(GeneratedExportRepository.class);
        siriWritingService = new SiriWritingService();
        publishedJourneyRepository = mock(NeTExPublishedJourneyRepository.class);

        service = new SiriEtGenerationService(
                stationRepository,
                gtfsTrainRepository,
                petiStopSource,
                siriWritingService,
                generatedExportRepository,
                publishedJourneyRepository);
    }

    // ===== HELPERS =====

    /** The full happy path: train 59 published in the DB, plus stations, PETI quays and the live train. */
    private void setupHappyPath() {
        seedPublished(publishedJourney59());
        setupStationsPetiAndTrains(train59());
    }

    /** Stub the newest published dataset version to contain the given journeys. */
    private void seedPublished(final NeTExPublishedJourney... journeys) {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(DATASET_VERSION);
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(eq(DATASET_VERSION), any()))
                .thenReturn(List.of(journeys));
    }

    /** The NeTEx-published refs for train 59 on TODAY, with planned tracks matching its live stops. */
    private static NeTExPublishedJourney publishedJourney59() {
        final NeTExPublishedJourney journey = new NeTExPublishedJourney(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:59", DATASET_VERSION, DateProvider.nowInHelsinki());
        journey.addTrack(new NeTExPublishedJourneyTrack("HKI", "7"));
        journey.addTrack(new NeTExPublishedJourneyTrack("TPE", "1"));
        journey.addTrack(new NeTExPublishedJourneyTrack("OL", "1"));
        return journey;
    }

    private void setupStationsPetiAndTrains(final GTFSTrain... trains) {
        final List<Station> stations = List.of(
                createStation("HKI", 1),
                createStation("TPE", 160),
                createStation("OL", 280));
        when(stationRepository.findAll()).thenReturn(stations);

        final PetiUicMatcher matcher = new PetiUicMatcher(List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null, null, null)))));
        when(petiStopSource.getMatcher()).thenReturn(matcher);

        when(gtfsTrainRepository.findBySourceVersionAndIdIn(anyLong(), any()))
                .thenReturn(List.of(trains));
    }

    private static GTFSTrain train59() {
        final GTFSTrain train = createTrain(59L, TODAY);
        addStop(train, "HKI", null, ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "TPE", ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI), "1");
        addStop(train, "OL", ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        return train;
    }

    private static GTFSTrain train999() {
        final GTFSTrain train = createTrain(999L, TODAY);
        addStop(train, "HKI", null, ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL", ZonedDateTime.of(2026, 7, 15, 16, 0, 0, 0, HELSINKI), null, "1");
        return train;
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

    private static Station createStation(final String shortCode, final int uicCode) {
        final Station station = new Station();
        station.shortCode = shortCode;
        station.uicCode = uicCode;
        station.passengerTraffic = true;
        return station;
    }

    @SuppressWarnings("unchecked")
    private List<GeneratedExport> capturePersistedExports() {
        final ArgumentCaptor<Collection<GeneratedExport>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(generatedExportRepository).persist(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    // ===== GEN-01: Happy path — persists GeneratedExport with fileName "siri-et.xml" =====

    @Test
    void givenHappyPath_whenGenerate_thenPersistsWithCorrectFileName() {
        setupHappyPath();

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        assertEquals(1, exports.size());
        assertEquals("siri-et.xml", exports.get(0).fileName);
    }

    // ===== GEN-02: Happy path — persisted bytes are schema-valid SIRI-ET XML =====

    @Test
    void givenHappyPath_whenGenerate_thenPersistedBytesAreValidSiriXml() {
        setupHappyPath();

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        assertNotNull(exports.get(0).data);
        assertTrue(exports.get(0).data.length > 0);
        assertTrue(siriWritingService.isSchemaValid(exports.get(0).data));
    }

    // ===== GEN-03: Happy path — persisted doc references the published ServiceJourney id for train 59 =====

    @Test
    void givenHappyPath_whenGenerate_thenDocContainsEvjForTrain59() {
        setupHappyPath();

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.get(0).data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"),
                "Expected published ServiceJourney id for train 59 in XML");
    }

    // ===== GEN-04: Journey refs are read from the published dataset for the operating-day window =====

    @Test
    void givenGenerate_thenReadsPublishedJourneysForOperatingDayWindow() {
        setupHappyPath();
        final LocalDate yesterday = TODAY.minusDays(1);

        service.generate();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<LocalDate>> datesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(publishedJourneyRepository)
                .findByDatasetVersionAndDepartureDatesFetchTracks(eq(DATASET_VERSION), datesCaptor.capture());
        assertTrue(datesCaptor.getValue().contains(TODAY), "lookup window must include today");
        assertTrue(datesCaptor.getValue().contains(yesterday), "lookup window must include yesterday");
    }

    // ===== GEN-05: StationUicLookup built from StationRepository.findAll() =====

    @Test
    void givenStations_whenGenerate_thenStationRepositoryFindAllCalled() {
        setupHappyPath();

        service.generate();

        verify(stationRepository).findAll();
    }

    // ===== GEN-06: Prebuilt PetiUicMatcher is obtained once per cycle =====

    @Test
    void givenPetiStopSource_whenGenerate_thenGetMatcherCalledExactlyOnce() {
        setupHappyPath();

        service.generate();

        verify(petiStopSource, times(1)).getMatcher();
    }

    // ===== GEN-07: Live train with no published journey is skipped =====

    @Test
    void givenTrainWithNoPublishedJourney_whenGenerate_thenOnlyPublishedTrainInOutput() {
        seedPublished(publishedJourney59());
        setupStationsPetiAndTrains(train59(), train999());

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.get(0).data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"), "Expected train 59 in output");
        assertFalse(xml.contains("999"), "Train 999 should not appear (no published journey)");
    }

    // ===== GEN-07b: The live-train fetch is driven by the published journey ids (not a re-applied filter) =====

    @Test
    void givenGenerate_thenFetchesTrainsByPublishedJourneyIds() {
        setupHappyPath();

        service.generate();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<TrainId>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(gtfsTrainRepository).findBySourceVersionAndIdIn(eq(0L), idsCaptor.capture());
        assertTrue(idsCaptor.getValue().contains(new TrainId(59L, TODAY)),
                "train fetch must be driven by the published journey ids");
    }

    // ===== GEN-08: Published-journey repository error → no persist, no throw =====

    @Test
    void givenPublishedRepoException_whenGenerate_thenNoPersistAndNoThrow() {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-09: StationRepository error → no persist, no throw =====

    @Test
    void givenStationRepoException_whenGenerate_thenNoPersistAndNoThrow() {
        seedPublished(publishedJourney59());
        when(stationRepository.findAll()).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-10: No published NeTEx at all → cycle fails, nothing published (hard dependency) =====

    @Test
    void givenNoPublishedNeTEx_whenGenerate_thenFailsWithoutPublishing() {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(null);
        setupStationsPetiAndTrains(train59());

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-11: Published rows exist but none for the operating days → fails, nothing published =====

    @Test
    void givenNoPublishedJourneysForOperatingDays_whenGenerate_thenFailsWithoutPublishing() {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(DATASET_VERSION);
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(eq(DATASET_VERSION), any()))
                .thenReturn(List.of());
        setupStationsPetiAndTrains(train59());

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-12: Published journeys older than the freshness limit → fails, nothing published =====

    @Test
    void givenStalePublishedJourneys_whenGenerate_thenFailsWithoutPublishing() {
        final NeTExPublishedJourney stale = new NeTExPublishedJourney(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:59", DATASET_VERSION, DateProvider.nowInHelsinki().minusHours(30));
        seedPublished(stale);
        setupStationsPetiAndTrains(train59());

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }
}
