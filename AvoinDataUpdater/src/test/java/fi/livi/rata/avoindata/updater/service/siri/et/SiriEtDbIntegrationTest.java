package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.netex.NeTExPackageService;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * End-to-end DB round-trip for TICKET-04.3: NeTEx generation persists the resolved journey refs to the real
 * database (dbrail docker compose), then SIRI-ET generation reads them back from the DB and publishes a doc
 * that references the same published {@code ServiceJourney} id. The external systems — RIPA/LIIKE schedules,
 * PETI stops, station metadata and the live-train store — are mocked; only the persistence layer is real.
 *
 * <p>Requires the {@code dbrail/} MySQL to be running (see {@code dbrail/docker-compose.yml}).
 */
class SiriEtDbIntegrationTest extends BaseTest {

    private static final ZoneId HELSINKI = ZoneId.of("Europe/Helsinki");
    private static final LocalDate TODAY = DateProvider.dateInHelsinki();
    private static final long TRAIN_NUMBER = 100L;
    private static final long SCHEDULE_ID = 1L;

    // RIPA/LIIKE (schedules), PETI (stops), station metadata and the live-train store are the external inputs.
    @MockitoBean
    private ScheduleProviderService scheduleProviderService;
    @MockitoBean
    private StationRepository stationRepository;
    @MockitoBean
    private GTFSTrainRepository gtfsTrainRepository;
    @MockitoBean
    private PetiStopSource petiStopSource;

    @Autowired
    private NeTExPackageService neTExPackageService;
    @Autowired
    private SiriEtGenerationService siriEtGenerationService;
    @Autowired
    private NeTExPublishedJourneyRepository publishedJourneyRepository;
    @Autowired
    private GeneratedExportRepository generatedExportRepository;

    @BeforeEach
    @AfterEach
    void clearDb() {
        generatedExportRepository.deleteAll();
        publishedJourneyRepository.deleteAll();
    }

    @Test
    void netexGeneratedToDb_thenSiriEtReadsRefsFromDb() throws Exception {
        // given — the external systems return a single passenger train 100 (HKI → TPE) valid today
        final List<Station> stations = List.of(station("HKI", 1), station("TPE", 160));
        final List<PetiStop> petiStops = petiStops();
        when(stationRepository.findAll()).thenReturn(stations);
        when(scheduleProviderService.getAdhocSchedules(any())).thenReturn(List.of());
        when(scheduleProviderService.getRegularSchedules(any())).thenReturn(List.of(schedule100()));
        when(petiStopSource.getStops()).thenReturn(petiStops);
        when(petiStopSource.getMatcher()).thenReturn(new PetiUicMatcher(petiStops));
        when(petiStopSource.getSnapshotAgeSeconds()).thenReturn(0L);
        when(gtfsTrainRepository.findBySourceVersionAndIdIn(anyLong(), any()))
                .thenReturn(List.of(liveTrain100()));

        // when — NeTEx generation persists the ZIP package + the resolved journey refs to the real DB
        neTExPackageService.generatePackage();

        // then — the published journey for (100, today) is in the database, with its planned tracks
        final Long version = publishedJourneyRepository.getMaxDatasetVersion();
        assertNotNull(version, "NeTEx generation must persist a dataset version");
        final List<NeTExPublishedJourney> published =
                publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(version, List.of(TODAY));
        assertFalse(published.isEmpty(), "expected a published journey for train 100 on today");
        final NeTExPublishedJourney journey = published.getFirst();
        final String serviceJourneyId = journey.serviceJourneyId;
        assertTrue(serviceJourneyId.contains(String.valueOf(TRAIN_NUMBER)),
                "serviceJourneyId should reference train 100, was " + serviceJourneyId);
        assertFalse(journey.tracks.isEmpty(), "expected persisted planned tracks");
        assertNotNull(generatedExportRepository.findFirstByFileNameOrderByIdDesc("FTR-netex.zip"),
                "NeTEx ZIP package must be published");

        // when — SIRI-ET generation reads the refs back from the DB (no RIPA)
        siriEtGenerationService.generate();

        // then — the persisted SIRI-ET doc references the same DB-published ServiceJourney id
        final GeneratedExport siri = generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-et.xml");
        assertNotNull(siri, "SIRI-ET must be published");
        final String siriXml = new String(siri.data, StandardCharsets.UTF_8);
        assertTrue(siriXml.contains(serviceJourneyId),
                "SIRI-ET must reference the DB-published ServiceJourney id " + serviceJourneyId);
    }

    @Test
    void givenNoPublishedNeTEx_whenSiriEtGenerate_thenNothingPublished() {
        // given — no NeTEx has been generated (empty published-journey table)
        when(stationRepository.findAll()).thenReturn(List.of(station("HKI", 1), station("TPE", 160)));

        // when
        siriEtGenerationService.generate();

        // then — SIRI has a hard dependency on the published NeTEx: it fails the cycle and publishes nothing
        assertNull(generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-et.xml"),
                "SIRI-ET must not be published without NeTEx refs in the DB");
    }

    // ===== builders =====

    private static Station station(final String shortCode, final int uicCode) {
        final Station station = new Station();
        station.shortCode = shortCode;
        station.name = shortCode + " station";
        station.uicCode = uicCode;
        station.passengerTraffic = true;
        station.latitude = new BigDecimal("60.17");
        station.longitude = new BigDecimal("24.94");
        return station;
    }

    private static List<PetiStop> petiStops() {
        return List.of(
                new PetiStop("FSR:StopPlace:HKI", 1_000_001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1_000_160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-2", "2", null, null, null))));
    }

    /** A regular passenger schedule for train 100 (HKI → TPE) that runs every day across the feed horizon. */
    private static Schedule schedule100() {
        final Schedule schedule = new Schedule();
        schedule.id = SCHEDULE_ID;
        schedule.trainNumber = TRAIN_NUMBER;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.startDate = TODAY.minusDays(7);
        schedule.endDate = TODAY.plusDays(30);
        schedule.effectiveFrom = TODAY.minusDays(7);
        schedule.changeType = "L";
        schedule.capacityId = "cap-100";
        schedule.typeCode = "L";
        schedule.runOnMonday = true;
        schedule.runOnTuesday = true;
        schedule.runOnWednesday = true;
        schedule.runOnThursday = true;
        schedule.runOnFriday = true;
        schedule.runOnSaturday = true;
        schedule.runOnSunday = true;
        schedule.scheduleCancellations = new HashSet<>();
        schedule.scheduleExceptions = new HashSet<>();
        schedule.operator = new Operator(10, "vr");

        final TrainCategory category = new TrainCategory();
        category.name = "Long-distance";
        final TrainType trainType = new TrainType();
        trainType.name = "IC";
        trainType.commercial = true;
        trainType.trainCategory = category;
        schedule.trainType = trainType;
        schedule.trainCategory = category;
        schedule.commuterLineId = null;

        schedule.scheduleRows = new ArrayList<>();
        schedule.scheduleRows.add(scheduleRow("HKI", 1, "1", false, true));
        schedule.scheduleRows.add(scheduleRow("TPE", 160, "2", true, false));
        return schedule;
    }

    private static ScheduleRow scheduleRow(final String shortCode, final int uic, final String track,
                                           final boolean arrival, final boolean departure) {
        final ScheduleRow row = new ScheduleRow();
        row.station = new StationEmbeddable(shortCode, uic, "FI");
        row.commercialTrack = track;
        if (arrival) {
            final ScheduleRowPart part = new ScheduleRowPart();
            part.timestamp = Duration.ofHours(9);
            part.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
            part.scheduleRow = row;
            row.arrival = part;
        }
        if (departure) {
            final ScheduleRowPart part = new ScheduleRowPart();
            part.timestamp = Duration.ofHours(8);
            part.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
            part.scheduleRow = row;
            row.departure = part;
        }
        return row;
    }

    /** The live train 100 dated today: departs HKI (track 1), arrives TPE (track 2). */
    private static GTFSTrain liveTrain100() {
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(TRAIN_NUMBER, TODAY);
        train.cancelled = false;
        train.timeTableRows = new ArrayList<>();
        addStop(train, "HKI", null, ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "1");
        addStop(train, "TPE", ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI), null, "2");
        return train;
    }

    private static void addStop(final GTFSTrain train, final String stationShortCode,
                                final ZonedDateTime arrivalTime, final ZonedDateTime departureTime,
                                final String track) {
        if (arrivalTime != null) {
            train.timeTableRows.add(stopRow(train, stationShortCode, TimeTableRow.TimeTableRowType.ARRIVAL,
                    arrivalTime, track));
        }
        if (departureTime != null) {
            train.timeTableRows.add(stopRow(train, stationShortCode, TimeTableRow.TimeTableRowType.DEPARTURE,
                    departureTime, track));
        }
    }

    private static GTFSTimeTableRow stopRow(final GTFSTrain train, final String stationShortCode,
                                            final TimeTableRow.TimeTableRowType type, final ZonedDateTime time,
                                            final String track) {
        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.stationShortCode = stationShortCode;
        row.type = type;
        row.scheduledTime = time;
        row.commercialStop = true;
        row.commercialTrack = track;
        row.train = train;
        return row;
    }
}
