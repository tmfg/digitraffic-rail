package fi.livi.rata.avoindata.updater.service.siri.vm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TimeTableRowFactory;
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
 * End-to-end DB round-trip for SIRI-VM, the VM counterpart of {@code SiriEtDbIntegrationTest}: NeTEx refs, a
 * live {@code train_location} row and its {@code time_table_row}s go into the real database, and SIRI-VM
 * reads them back and publishes a doc referencing the same ServiceJourney id.
 *
 * <p>Unlike the ET test this does <em>not</em> mock {@code GTFSTrainRepository} — the feed's whole input comes
 * from its native "next commercial stop" query, the part only a real database can exercise. Schedules, PETI
 * stops and station metadata stay mocked.
 *
 * <p>Requires the dbrail/ MySQL to be running (see dbrail/docker-compose.yml).
 */
class SiriVmDbIntegrationTest extends BaseTest {

    private static final LocalDate TODAY = DateProvider.dateInHelsinki();
    // Below 2000, so TrainLocationRepository.findLatestForPassengerTrains accepts it as a passenger train.
    private static final long TRAIN_NUMBER = 100L;
    private static final long SCHEDULE_ID = 1L;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    // Schedules, PETI (stops) and station metadata are the external inputs; the live position and its
    // timetable rows are stored in the real database below.
    @MockitoBean
    private ScheduleProviderService scheduleProviderService;
    @MockitoBean
    private StationRepository stationRepository;
    @MockitoBean
    private PetiStopSource petiStopSource;

    @Autowired
    private NeTExPackageService neTExPackageService;
    @Autowired
    private SiriVmGenerationService siriVmGenerationService;
    @Autowired
    private NeTExPublishedJourneyRepository publishedJourneyRepository;
    @Autowired
    private GeneratedExportRepository generatedExportRepository;
    @Autowired
    private TrainRepository trainRepository;
    @Autowired
    private TimeTableRowRepository timeTableRowRepository;
    @Autowired
    private TrainLocationRepository trainLocationRepository;
    @Autowired
    private TimeTableRowFactory timeTableRowFactory;

    @BeforeEach
    @AfterEach
    void clearDb() {
        generatedExportRepository.deleteAll();
        publishedJourneyRepository.deleteAll();
        trainLocationRepository.deleteAll();
        timeTableRowRepository.deleteAll();
        trainRepository.deleteAll();
    }

    @Test
    void netexGeneratedToDb_thenSiriVmReadsLocationAndRefsFromDb() throws Exception {
        // given — the external systems return a single passenger train 100 (HKI → TPE) valid today
        final List<PetiStop> petiStops = petiStops();
        when(stationRepository.findAll()).thenReturn(List.of(station("HKI", 1), station("TPE", 160)));
        when(scheduleProviderService.getAdhocSchedules(any())).thenReturn(List.of());
        when(scheduleProviderService.getRegularSchedules(any())).thenReturn(List.of(schedule100()));
        when(petiStopSource.getStops()).thenReturn(petiStops);
        when(petiStopSource.getMatcher()).thenReturn(new PetiUicMatcher(petiStops));
        when(petiStopSource.getMatcher(any())).thenReturn(new PetiUicMatcher(petiStops));
        when(petiStopSource.getSnapshotAgeSeconds()).thenReturn(0.0);

        // and — the live train and a just-reported position for it are in the database
        final Train train = saveLiveTrain100();
        final TrainLocation location = saveLocationFor(train);

        // when — NeTEx generation persists the resolved journey refs to the real DB
        neTExPackageService.generatePackage();

        // then — the published journey for (100, today) is in the database
        final Long version = publishedJourneyRepository.getMaxDatasetVersion();
        assertNotNull(version, "NeTEx generation must persist a dataset version");
        final List<NeTExPublishedJourney> published = publishedJourneyRepository
                .findByDatasetVersionAndDepartureDatesFetchTracks(version, List.of(TODAY));
        assertFalse(published.isEmpty(), "expected a published journey for train 100 on today");
        final String serviceJourneyId = published.getFirst().serviceJourneyId;

        // and — the position resolves through the real native query to the train's upcoming commercial stop
        assertNotNull(location.id, "the live position must be persisted");

        // when — SIRI-VM generation reads the position and the refs back from the DB
        siriVmGenerationService.generate();

        // then — the published doc ties the live position to the same DB-published ServiceJourney
        final GeneratedExport siri = generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-vm.xml");
        assertNotNull(siri, "SIRI-VM must be published");
        final String siriXml = new String(siri.data, StandardCharsets.UTF_8);
        assertTrue(siriXml.contains(serviceJourneyId),
                "SIRI-VM must reference the DB-published ServiceJourney id " + serviceJourneyId);
        assertTrue(siriXml.contains("<VehicleRef>" + TRAIN_NUMBER + "</VehicleRef>"),
                "SIRI-VM must identify the vehicle by its train number");
        // The coordinates come out of the geometry column through st_x/st_y, so a wrong axis order would
        // only show here, at the end of the real round-trip.
        assertTrue(siriXml.contains("<Longitude>24.940000</Longitude>"),
                "SIRI-VM must carry the stored longitude, was: " + siriXml);
        assertTrue(siriXml.contains("<Latitude>60.170000</Latitude>"),
                "SIRI-VM must carry the stored latitude, was: " + siriXml);
        // MonitoredCall is what the native "next commercial stop" query resolves: HKI is the first commercial
        // stop and the train has not departed, so HKI track 1's quay must be reported.
        assertTrue(siriXml.contains("<StopPointRef>FSR:Quay:HKI-1</StopPointRef>"),
                "SIRI-VM must report the upcoming stop resolved from the live timetable rows, was: " + siriXml);
    }

    @Test
    void givenNoPublishedNeTEx_whenSiriVmGenerate_thenNothingPublished() {
        // given — a live position exists, but no NeTEx has been generated (empty published-journey table)
        when(stationRepository.findAll()).thenReturn(List.of(station("HKI", 1), station("TPE", 160)));
        saveLocationFor(saveLiveTrain100());

        // when
        siriVmGenerationService.generate();

        // then — SIRI has a hard dependency on the published NeTEx: it fails the cycle and publishes nothing
        assertNull(generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-vm.xml"),
                "SIRI-VM must not be published without NeTEx refs in the DB");
    }

    // ===== builders =====

    /**
     * Train 100 dated today, at its origin HKI (track 1) and bound for TPE (track 2). Both rows are pending
     * with a future live estimate — what the native "next commercial stop" query needs to report a stop.
     */
    private Train saveLiveTrain100() {
        final ZonedDateTime now = DateProvider.nowInHelsinki();
        Train train = new Train(TRAIN_NUMBER, TODAY, 10, "vr", 1L, 1L, null, true, false, 1L,
                Train.TimetableType.REGULAR, now);
        train.sourceVersion = 1L; // stamped from the payload version in production; the queries skip 0
        train.timeTableRows = new ArrayList<>();
        train = trainRepository.save(train);

        final TimeTableRow departure = timeTableRowFactory.create(train, now.plusHours(1), null,
                new StationEmbeddable("HKI", 1, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE);
        departure.liveEstimateTime = now.plusHours(1);
        final TimeTableRow arrival = timeTableRowFactory.create(train, now.plusHours(2), null,
                new StationEmbeddable("TPE", 160, "FI"), TimeTableRow.TimeTableRowType.ARRIVAL);
        arrival.commercialTrack = "2";
        arrival.liveEstimateTime = now.plusHours(2);

        train.timeTableRows = timeTableRowRepository.saveAll(List.of(departure, arrival));
        return train;
    }

    /** A position reported just now, so it falls inside SIRI-VM's freshness window. */
    private TrainLocation saveLocationFor(final Train train) {
        final TrainLocation location = new TrainLocation();
        location.trainLocationId = new TrainLocationId(train.id.trainNumber, train.id.departureDate,
                DateProvider.nowInHelsinki());
        location.location = geometryFactory.createPoint(new Coordinate(24.94, 60.17));
        location.speed = 80;
        location.accuracy = 10;
        return trainLocationRepository.save(location);
    }

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
}
