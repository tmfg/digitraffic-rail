package fi.livi.rata.avoindata.updater.service.siri.vm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
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
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static fi.livi.rata.avoindata.common.utils.DateProvider.ZONE_ID_HKI;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SIRI-VM generation has a hard dependency on the NeTEx-published journey refs in the DB: it reads the
 * journey/line refs and planned tracks from {@link NeTExPublishedJourneyRepository} and fails the cycle — with
 * no live fallback — when they are missing, absent for the operating days, or stale.
 * <p>
 * It also loads live train locations from {@link TrainLocationRepository} and resolves each location to a
 * published journey, emitting a VehicleActivity for each successful resolution.
 */
class SiriVmGenerationServiceTest {

    // Trains are dated "today" (Helsinki) so they are current-operating-day journeys, never stale carryovers.
    private static final LocalDate TODAY = DateProvider.dateInHelsinki();
    private static final long DATASET_VERSION = 5L;

    private StationRepository stationRepository;
    private TrainLocationRepository trainLocationRepository;
    private GTFSTrainRepository gtfsTrainRepository;
    private PetiStopSource petiStopSource;
    private GeneratedExportRepository generatedExportRepository;
    private SiriWritingService siriWritingService;
    private NeTExPublishedJourneyRepository publishedJourneyRepository;

    private SiriVmGenerationService service;

    @BeforeEach
    void setUp() {
        stationRepository = mock(StationRepository.class);
        trainLocationRepository = mock(TrainLocationRepository.class);
        gtfsTrainRepository = mock(GTFSTrainRepository.class);
        petiStopSource = mock(PetiStopSource.class);
        generatedExportRepository = mock(GeneratedExportRepository.class);
        siriWritingService = new SiriWritingService();
        publishedJourneyRepository = mock(NeTExPublishedJourneyRepository.class);

        service = new SiriVmGenerationService(
                stationRepository,
                trainLocationRepository,
                gtfsTrainRepository,
                petiStopSource,
                siriWritingService,
                generatedExportRepository,
                publishedJourneyRepository);
    }

    // ===== HELPERS =====

    /**
     * The full happy path: train 59 published in the DB, stations, PETI quays, and a live location.
     */
    private void setupHappyPath() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        setupLiveLocation(location59());
    }

    /**
     * Stub the newest published dataset version to contain the given journeys.
     */
    private void seedPublished(final NeTExPublishedJourney... journeys) {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(DATASET_VERSION);
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(eq(DATASET_VERSION), any()))
                .thenReturn(List.of(journeys));
    }

    /**
     * The NeTEx-published refs for train 59 on TODAY, with planned tracks matching its live stops.
     */
    private static NeTExPublishedJourney publishedJourney59() {
        final NeTExPublishedJourney journey = new NeTExPublishedJourney(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:59", DATASET_VERSION, DateProvider.nowInHelsinki());
        journey.addTrack(new NeTExPublishedJourneyTrack("HKI", "7", 0, 0));
        journey.addTrack(new NeTExPublishedJourneyTrack("TPE", "1", 0, 1));
        journey.addTrack(new NeTExPublishedJourneyTrack("OL", "1", 0, 2));
        return journey;
    }

    /**
     * Setup stations and PETI quays for the happy path.
     */
    private void setupStationsAndPeti() {
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
        when(petiStopSource.getSnapshotAgeSeconds()).thenReturn(300.0);
    }

    /**
     * Setup a live location for train 59 at station TPE.
     */
    private void setupLiveLocation(final TestGTFSTrainLocation location) {
        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of(1L));
        when(gtfsTrainRepository.getTrainLocations(List.of(1L))).thenReturn(List.of(location));
    }

    /**
     * A live train location for train 59 at station TPE, within the LOCATION_MAX_AGE_MINUTES window.
     */
    private static TestGTFSTrainLocation location59() {
        return new TestGTFSTrainLocation(
                1L,
                TODAY,
                59L,
                ZonedDateTime.of(2026, 7, 15, 9, 33, 0, 0, ZONE_ID_HKI),  // between TPE arrival and departure
                25.759588,
                61.437778,
                60,
                10,
                "TPE",
                "1",
                false,
                180,
                false,
                null);
    }

    /**
     * Another live train location for train 99 (unpublished train).
     */
    private static TestGTFSTrainLocation location99() {
        return new TestGTFSTrainLocation(
                2L,
                TODAY,
                99L,
                ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZONE_ID_HKI),
                25.759588,
                61.437778,
                80,
                10,
                "HKI",
                "7",
                false,
                0,
                true,
                null);
    }

    private static Station createStation(final String shortCode, final int uicCode) {
        final Station station = new Station();
        station.shortCode = shortCode;
        station.uicCode = uicCode;
        station.passengerTraffic = true;
        return station;
    }

    /**
     * train 59's full time_table_row list, matching {@link #publishedJourney59()}'s planned tracks, and timed
     * relative to the real clock (not a fixed date) so its TPE stop is still "current" against the fallback's
     * {@code DateProvider.nowInHelsinki()} lookup regardless of when the test actually runs.
     */
    private static GTFSTrain train59Rows() {
        final ZonedDateTime tpeArrival = DateProvider.nowInHelsinki().minusMinutes(3);
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(59L, TODAY);
        train.cancelled = false;
        train.timeTableRows = new ArrayList<>();
        addRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE, tpeArrival.minusMinutes(30), "7");
        addRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL, tpeArrival, "1");
        addRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE, tpeArrival.plusMinutes(5), "1");
        addRow(train, "OL", TimeTableRow.TimeTableRowType.ARRIVAL, tpeArrival.plusHours(2), "1");
        return train;
    }

    /**
     * Same rows as {@link #train59Rows()}, but with every row's actualTime set - the train has run its whole
     * journey and is now dwelling at its terminus (OL), which as a terminus has only an ARRIVAL row (no
     * DEPARTURE) - see CommercialStopVisits#resolveTerminusFallback.
     */
    private static GTFSTrain train59RowsArrivedAtTerminus() {
        final GTFSTrain train = train59Rows();
        for (final GTFSTimeTableRow row : train.timeTableRows) {
            row.actualTime = row.scheduledTime;
        }
        return train;
    }

    private static void addRow(final GTFSTrain train, final String stationShortCode,
                               final TimeTableRow.TimeTableRowType type, final ZonedDateTime scheduledTime,
                               final String track) {
        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.stationShortCode = stationShortCode;
        row.type = type;
        row.scheduledTime = scheduledTime;
        row.commercialStop = true;
        row.commercialTrack = track;
        row.cancelled = false;
        row.train = train;
        // No actual time yet (not-yet-completed row) - mirrors a fresh, on-time live estimate, which is what
        // Stop#isRowEligible requires (see CommercialStopVisits) for the row to count as the current visit.
        row.liveEstimateTime = scheduledTime;
        train.timeTableRows.add(row);
    }

    private List<GeneratedExport> capturePersistedExports() {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<GeneratedExport>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(generatedExportRepository).persist(captor.capture());
        return List.copyOf(captor.getValue());
    }

    // ===== GEN-VM-01: Happy path — persists GeneratedExport with fileName "siri-vm.xml" =====

    @Test
    void givenHappyPath_whenGenerate_thenPersistsWithCorrectFileName() {
        setupHappyPath();

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        assertEquals(1, exports.size());
        assertEquals("siri-vm.xml", exports.getFirst().fileName);
    }

    // ===== GEN-VM-01b: Null commercialTrack with unknownTrack=false must still trigger the planned-track
    // fallback prefetch — regression test for a review-reported bug where the prefetch filter only checked
    // unknownTrack, silently missing trains whose live track was null for a different, unrelated reason
    // (e.g. commercial_track not yet populated), which then dropped a resolvable MonitoredCall entirely. =====

    @Test
    void givenNullCommercialTrackWithoutUnknownTrackFlag_whenGenerate_thenStillResolvesPlannedTrackFallback() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        // unknownTrack=false (not the usual fallback trigger) but commercialTrack=null - actualTrackOf() still
        // returns null here, so the converter still needs the planned-track fallback and its row prefetch.
        final TestGTFSTrainLocation location = new TestGTFSTrainLocation(
                1L, TODAY, 59L, DateProvider.nowInHelsinki().minusMinutes(1),
                25.759588, 61.437778, 60, 10, "TPE", null, false, 180, false, null);
        setupLiveLocation(location);
        when(gtfsTrainRepository.findBySourceVersionAndIdIn(anyLong(), any())).thenReturn(List.of(train59Rows()));

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FSR:Quay:TPE-1"),
                "Expected the planned-track fallback to resolve TPE's MonitoredCall even though "
                        + "unknownTrack=false (only commercialTrack was null)");
    }

    // ===== GEN-VM-01c: Blank ("") commercialTrack with unknownTrack=false must also trigger the planned-track
    // fallback prefetch — regression test for a review-reported bug: ingestion represents a missing/cleared
    // commercial track as "" (see ScheduleToTrainConverter.emptyCommercialTrackInTimeTableRows /
    // TimeTableRowDeserializer), not null, but the prefetch filter's underlying actualTrackOf() treated any
    // non-null string - including "" - as a usable live track, so it never entered the fallback and quay
    // resolution for "" silently failed, omitting the MonitoredCall. =====

    @Test
    void givenBlankCommercialTrackWithoutUnknownTrackFlag_whenGenerate_thenStillResolvesPlannedTrackFallback() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        final TestGTFSTrainLocation location = new TestGTFSTrainLocation(
                1L, TODAY, 59L, DateProvider.nowInHelsinki().minusMinutes(1),
                25.759588, 61.437778, 60, 10, "TPE", "", false, 180, false, null);
        setupLiveLocation(location);
        when(gtfsTrainRepository.findBySourceVersionAndIdIn(anyLong(), any())).thenReturn(List.of(train59Rows()));

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FSR:Quay:TPE-1"),
                "Expected the planned-track fallback to resolve TPE's MonitoredCall even though "
                        + "commercialTrack was blank rather than null");
    }

    // ===== GEN-VM-01e: A location whose stationShortCode is null (GTFSTrainRepository.getTrainLocations' own
    // "next stop" query resolved nothing - the train has already arrived at its terminus, see that query's
    // javadoc) must be filled in via the terminus fallback (CommercialStopVisits#resolveTerminusFallback),
    // resolving the train's arrived terminus (OL) as its MonitoredCall/VehicleAtStop from the train's full row
    // list - the same prefetch mechanism as the planned-track fallback above. =====

    @Test
    void givenNoNextStopResolvedByQuery_whenGenerate_thenResolvesTerminusFallbackFromFullRowList() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        final TestGTFSTrainLocation location = new TestGTFSTrainLocation(
                1L, TODAY, 59L, DateProvider.nowInHelsinki().minusMinutes(1),
                25.759588, 61.437778, 0, 10, null, null, null, null, null, null);
        setupLiveLocation(location);
        when(gtfsTrainRepository.findBySourceVersionAndIdIn(anyLong(), any()))
                .thenReturn(List.of(train59RowsArrivedAtTerminus()));

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FSR:Quay:OL-1"),
                "Expected the terminus fallback to resolve OL (the train's arrived terminus) as the "
                        + "MonitoredCall, even though the SQL query itself resolved no next stop at all");
    }

    // ===== GEN-VM-02: Happy path — persisted bytes are schema-valid SIRI-VM XML =====

    @Test
    void givenHappyPath_whenGenerate_thenPersistedBytesAreValidSiriXml() {
        setupHappyPath();

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        assertNotNull(exports.getFirst().data);
        assertTrue(exports.getFirst().data.length > 0);
        assertTrue(siriWritingService.isSchemaValid(exports.getFirst().data));
    }

    // ===== GEN-VM-03: Happy path — persisted doc references the published ServiceJourney id for train 59 =====

    @Test
    void givenHappyPath_whenGenerate_thenDocContainsEvjForTrain59() {
        setupHappyPath();

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"),
                "Expected published ServiceJourney id for train 59 in XML");
    }

    // ===== GEN-VM-04: Journey refs are read from the published dataset for the operating-day window =====

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

    // ===== GEN-VM-05: StationUicLookup built from StationRepository.findAll() =====

    @Test
    void givenStations_whenGenerate_thenStationRepositoryFindAllCalled() {
        setupHappyPath();

        service.generate();

        verify(stationRepository).findAll();
    }

    // ===== GEN-VM-06: Prebuilt PetiUicMatcher is obtained once per cycle =====

    @Test
    void givenPetiStopSource_whenGenerate_thenGetMatcherCalledExactlyOnce() {
        setupHappyPath();

        service.generate();

        verify(petiStopSource, times(1)).getMatcher();
    }

    // ===== GEN-VM-07: Live locations with no published journey are skipped =====

    @Test
    void givenLocationWithNoPublishedJourney_whenGenerate_thenOnlyPublishedTrainInOutput() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of(1L, 2L));
        when(gtfsTrainRepository.getTrainLocations(List.of(1L, 2L)))
                .thenReturn(List.of(location59(), location99()));

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"), "Expected train 59 in output");
        // Checking for a literal "99" substring in the whole document is unsafe: the response/recorded
        // timestamps are real wall-clock values (not frozen in this test) and can coincidentally contain "99"
        // in any of their digits (seconds, milliseconds, etc.), making the assertion flaky. Counting
        // VehicleActivity elements instead only depends on how many journeys were actually resolved.
        assertEquals(1, StringUtils.countMatches(xml, "<VehicleActivity>"),
                "Train 99 should not appear (no published journey)");
    }

    // ===== GEN-VM-08: Live locations are fetched using findLatestForPassengerTrains =====

    @Test
    void givenGenerate_thenFetchesLatestLocationsWithinAgeWindow() {
        setupHappyPath();

        service.generate();

        final ArgumentCaptor<ZonedDateTime> timeCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(trainLocationRepository).findLatestForPassengerTrains(timeCaptor.capture());
        // Verify the cutoff time is roughly 30 minutes in the past
        final ZonedDateTime capturedTime = timeCaptor.getValue();
        final long diffMinutes = java.time.Duration.between(capturedTime, DateProvider.nowInHelsinki()).toMinutes();
        assertTrue(diffMinutes >= 25 && diffMinutes <= 35,
                "cutoff time should be ~30 minutes in the past (got " + diffMinutes + " minutes)");
    }

    // ===== GEN-VM-09: Published-journey repository error → no persist, no throw =====

    @Test
    void givenPublishedRepoException_whenGenerate_thenNoPersistAndNoThrow() {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-10: StationRepository error → no persist, no throw =====

    @Test
    void givenStationRepoException_whenGenerate_thenNoPersistAndNoThrow() {
        seedPublished(publishedJourney59());
        when(stationRepository.findAll()).thenThrow(new RuntimeException("DB down"));
        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of(1L));

        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-11: No published NeTEx at all → cycle fails, nothing published (hard dependency) =====

    @Test
    void givenNoPublishedNeTEx_whenGenerate_thenFailsWithoutPublishing() {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(null);
        setupStationsAndPeti();
        setupLiveLocation(location59());

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-12: Published rows exist but none for the operating days → fails, nothing published =====

    @Test
    void givenNoPublishedJourneysForOperatingDays_whenGenerate_thenFailsWithoutPublishing() {
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(DATASET_VERSION);
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(eq(DATASET_VERSION), any()))
                .thenReturn(List.of());
        setupStationsAndPeti();
        setupLiveLocation(location59());

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-13: Published journeys older than the freshness limit → fails, nothing published =====

    @Test
    void givenStalePublishedJourneys_whenGenerate_thenFailsWithoutPublishing() {
        final NeTExPublishedJourney stale = new NeTExPublishedJourney(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:59", DATASET_VERSION, DateProvider.nowInHelsinki().minusHours(30));
        seedPublished(stale);
        setupStationsAndPeti();
        setupLiveLocation(location59());

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-15: Empty PETI snapshot → fail at prepare with a PETI_EMPTY reason, keep last-good (no persist) =====

    @Test
    void givenEmptyPetiSnapshot_whenGenerate_thenFailsAtPrepareWithPetiEmptyReason() {
        seedPublished(publishedJourney59());
        when(stationRepository.findAll()).thenReturn(List.of(
                createStation("HKI", 1), createStation("TPE", 160), createStation("OL", 280)));
        when(petiStopSource.getMatcher()).thenReturn(new PetiUicMatcher(List.of())); // empty PETI snapshot
        when(petiStopSource.getSnapshotAgeSeconds()).thenReturn(0.0);
        setupLiveLocation(location59());

        final Logger logger = (Logger) LoggerFactory.getLogger(SiriVmGenerationService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.generate();
        } finally {
            logger.detachAppender(appender);
        }

        verify(generatedExportRepository, never()).persist(any());
        final String wideEvent = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("rail.siri.journey_source.unavailable_reason="))
                .toList()
                .getLast();
        assertTrue(wideEvent.contains("rail.siri.journey_source.unavailable_reason=PETI_EMPTY"),
                "wide event must attribute the failure to PETI_EMPTY: " + wideEvent);
        assertTrue(wideEvent.contains("stage=prepare"), "must fail at the prepare stage: " + wideEvent);
    }

    // ===== GEN-VM-16: Empty locations (no recent live data) → success with zero activities emitted =====

    @Test
    void givenNoRecentLocations_whenGenerate_thenSucceedsWithZeroActivities() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of()); // no locations
        when(gtfsTrainRepository.getTrainLocations(List.of())).thenReturn(List.of());

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        assertEquals(1, exports.size());
        assertEquals("siri-vm.xml", exports.getFirst().fileName);
    }

    // ===== GEN-VM-17: Statistics are emitted correctly (locations received vs activities emitted) =====

    @Test
    void givenHappyPath_whenGenerate_thenStatisticsAreCorrect() {
        setupHappyPath();

        final Logger logger = (Logger) LoggerFactory.getLogger(SiriVmGenerationService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.generate();
        } finally {
            logger.detachAppender(appender);
        }

        final String wideEvent = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("rail.siri.locations.received="))
                .toList()
                .getLast();
        assertTrue(wideEvent.contains("rail.siri.locations.received=1"),
                "Expected 1 location received: " + wideEvent);
        assertTrue(wideEvent.contains("rail.siri.activities.emitted=1"),
                "Expected 1 activity emitted: " + wideEvent);
        assertTrue(wideEvent.contains("outcome=success"),
                "Expected success outcome: " + wideEvent);
    }

    // ===== GEN-VM-18: Multiple locations resolved from published journeys are emitted =====

    @Test
    void givenMultipleLocationsForPublishedTrains_whenGenerate_thenBothEmitted() {
        // Add another published journey for train 60
        final NeTExPublishedJourney journey60 = new NeTExPublishedJourney(
                new TrainId(60L, TODAY), "FTR:ServiceJourney:60-67890", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:60", DATASET_VERSION, DateProvider.nowInHelsinki());
        journey60.addTrack(new NeTExPublishedJourneyTrack("HKI", "7", 0, 0));
        journey60.addTrack(new NeTExPublishedJourneyTrack("OL", "1", 0, 1));

        seedPublished(publishedJourney59(), journey60);
        setupStationsAndPeti();

        // Location for train 59 at TPE
        final TestGTFSTrainLocation loc59 = location59();
        // Location for train 60 at OL
        final TestGTFSTrainLocation loc60 = new TestGTFSTrainLocation(
                3L, TODAY, 60L,
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, ZONE_ID_HKI),
                25.759588, 61.437778, 70, 10, "OL", "1", false, 0, false, null);

        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of(1L, 3L));
        when(gtfsTrainRepository.getTrainLocations(List.of(1L, 3L))).thenReturn(List.of(loc59, loc60));

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FTR:ServiceJourney:59-12345"), "Expected train 59 in output");
        assertTrue(xml.contains("FTR:ServiceJourney:60-67890"), "Expected train 60 in output");
    }

    // ===== GEN-VM-19: TrainLocationRepository error → no persist, no throw =====

    @Test
    void givenTrainLocationRepoException_whenGenerate_thenNoPersistAndNoThrow() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        when(trainLocationRepository.findLatestForPassengerTrains(any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-20: GTFSTrainRepository error → no persist, no throw =====

    @Test
    void givenGtfsTrainRepoException_whenGenerate_thenNoPersistAndNoThrow() {
        seedPublished(publishedJourney59());
        setupStationsAndPeti();
        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of(1L));
        when(gtfsTrainRepository.getTrainLocations(any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertDoesNotThrow(() -> service.generate());
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-21: Invalid SIRI output from marshalling → no persist, no throw =====

    @Test
    void givenInvalidSiriOutput_whenGenerate_thenNoPersistandNoThrow() {
        setupHappyPath();
        // Spy on a real SiriWritingService so buildEnvelope/marshalToXml/marshalToBytes actually run (a full
        // mock returns null from buildEnvelope by default, failing generation at the BUILD stage before it ever
        // reaches isSchemaValid - this test would then pass for any build failure, not just a schema-invalid
        // one). Only isSchemaValid is stubbed, to force generation to fail specifically at the VALIDATE stage.
        final SiriWritingService invalidService = spy(new SiriWritingService());
        doReturn(false).when(invalidService).isSchemaValid(any());

        final SiriVmGenerationService serviceWithInvalidWriter = new SiriVmGenerationService(
                stationRepository,
                trainLocationRepository,
                gtfsTrainRepository,
                petiStopSource,
                invalidService,
                generatedExportRepository,
                publishedJourneyRepository);

        assertDoesNotThrow(serviceWithInvalidWriter::generate);
        verify(generatedExportRepository, never()).persist(any());
    }

    // ===== GEN-VM-22: Export is created with current timestamp =====

    @Test
    void givenHappyPath_whenGenerate_thenExportHasCurrentTimestamp() {
        setupHappyPath();

        final ZonedDateTime beforeGeneration = DateProvider.nowInHelsinki();
        service.generate();
        final ZonedDateTime afterGeneration = DateProvider.nowInHelsinki();

        final List<GeneratedExport> exports = capturePersistedExports();
        assertNotNull(exports.getFirst().created);
        assertTrue(exports.getFirst().created.isAfter(beforeGeneration) || exports.getFirst().created.isEqual(beforeGeneration),
                "created timestamp should be after/equal generation start");
        assertTrue(exports.getFirst().created.isBefore(afterGeneration) || exports.getFirst().created.isEqual(afterGeneration),
                "created timestamp should be before/equal generation end");
    }

    /**
     * Documents a known, currently-accepted limitation rather than asserting a fix: when a journey's true origin
     * has an unknown planned track, {@code NeTExService.buildPublishedJourneyDrafts} never persists a track row
     * for it at all (unknown-track stops are filtered out before writing, not stored with a {@code null} track —
     * see {@code NeTExPublishedJourneyWriter}). So {@code SiriVmGenerationService.endpointOf(tracks, 0)} silently
     * picks the next known stop (here TPE) as the {@code OriginRef}, instead of the real origin (HKI).
     *
     * <p>This is tracked in the "Origin/Destination endpoint derivation" follow-up in
     * {@code docs/SIRI-VM-IMPLEMENTATION-PLAN.md} and is expected to become moot once the in-progress NeTEx-side
     * change (in a separate branch) lands, which guarantees every published stop always carries a planned track —
     * at that point this test (and the follow-up note) can be deleted.
     */
    @Test
    void givenOmittedOriginTrack_whenGenerate_thenOriginIdentityShiftsToNextKnownStop() {
        final NeTExPublishedJourney journey = new NeTExPublishedJourney(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:59", DATASET_VERSION, DateProvider.nowInHelsinki());
        // HKI's track was unknown at publish time, so the writer never persisted a row for it — it simply isn't
        // in the list (not stored with track=null; see NeTExService.buildPublishedJourneyDrafts).
        journey.addTrack(new NeTExPublishedJourneyTrack("TPE", "1", 0, 0));
        journey.addTrack(new NeTExPublishedJourneyTrack("OL", "1", 0, 1));
        seedPublished(journey);
        setupStationsAndPeti();
        setupLiveLocation(location59());

        service.generate();

        final List<GeneratedExport> exports = capturePersistedExports();
        final String xml = new String(exports.getFirst().data);
        assertTrue(xml.contains("FSR:Quay:TPE-1"), "origin currently (wrongly) resolves to the next known stop");
        assertFalse(xml.contains("FSR:Quay:HKI-7"), "the true origin's quay is not resolvable since it's missing"
                + " from the published tracks");
        assertTrue(xml.contains("FSR:Quay:OL-1"), "destination identity is unaffected since OL is still known");
    }

    /**
     * Regression test: if BUILD (conversion/marshalling) throws after locations have already been loaded from
     * the DB, the error event must still report the true received count instead of falling back to
     * {@code SiriVmStats.empty()}'s locationsReceived=0 - otherwise the wide event hides the affected batch
     * size on exactly the failures where it matters most. {@code getSpeed()} throwing simulates a failure
     * inside {@code VmJourneyConverter.convert} itself (BUILD stage), strictly after {@code prepareContext()}
     * (and its locations-received count) has already succeeded.
     */
    @Test
    void givenBuildThrowsAfterLocationsLoaded_whenGenerate_thenErrorEventReportsTrueLocationsReceived() {
        setupHappyPath();
        when(trainLocationRepository.findLatestForPassengerTrains(any())).thenReturn(List.of(1L));
        when(gtfsTrainRepository.getTrainLocations(List.of(1L)))
                .thenReturn(List.of(new ThrowingSpeedLocation(location59())));

        final Logger logger = (Logger) LoggerFactory.getLogger(SiriVmGenerationService.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.generate();
        } finally {
            logger.detachAppender(appender);
        }

        verify(generatedExportRepository, never()).persist(any());
        final String wideEvent = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("outcome=error") && m.contains("rail.siri.locations.received="))
                .toList()
                .getLast();
        assertTrue(wideEvent.contains("stage=build"), "must fail at the build stage: " + wideEvent);
        assertTrue(wideEvent.contains("rail.siri.locations.received=1"),
                "error event must retain the true received count, not fall back to 0: " + wideEvent);
    }

    /** Delegates every method to {@code delegate} except {@link #getSpeed()}, which always throws - used to
     * simulate a BUILD-stage (conversion) failure without needing malformed repository data. */
    private record ThrowingSpeedLocation(GTFSTrainLocation delegate) implements GTFSTrainLocation {
        @Override
        public long getId() {
            return delegate.getId();
        }

        @Override
        public LocalDate getDepartureDate() {
            return delegate.getDepartureDate();
        }

        @Override
        public long getTrainNumber() {
            return delegate.getTrainNumber();
        }

        @Override
        public ZonedDateTime getTimestamp() {
            return delegate.getTimestamp();
        }

        @Override
        public double getX() {
            return delegate.getX();
        }

        @Override
        public double getY() {
            return delegate.getY();
        }

        @Override
        public int getSpeed() {
            throw new RuntimeException("simulated BUILD-stage failure");
        }

        @Override
        public int getAccuracy() {
            return delegate.getAccuracy();
        }

        @Override
        public String getStationShortCode() {
            return delegate.getStationShortCode();
        }

        @Override
        public String getCommercialTrack() {
            return delegate.getCommercialTrack();
        }

        @Override
        public Boolean getUnknownTrack() {
            return delegate.getUnknownTrack();
        }

        @Override
        public Boolean getUnknownDelay() {
            return delegate.getUnknownDelay();
        }

        @Override
        public Integer getDelaySeconds() {
            return delegate.getDelaySeconds();
        }

        @Override
        public Boolean getVehicleAtStop() {
            return delegate.getVehicleAtStop();
        }

        @Override
        public Integer getVehicleAtStopValue() {
            return delegate.getVehicleAtStopValue();
        }
    }
}
