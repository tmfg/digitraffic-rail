package fi.livi.rata.avoindata.updater.service.siri.et;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.CallStatusEnumeration;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopAssignmentStructure;
import uk.org.siri.siri21.VehicleModesEnumeration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class SiriEtServiceTest {

    private static final ZoneId HELSINKI = SiriTimeConverter.HELSINKI_ZONE;
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, HELSINKI);
    private static final String PRODUCER_REF = "TEST";
    private static final String DATA_SOURCE = "FSR";

    private static final ResolvedJourney RESOLVED_59 =
            new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:59-12345"), new DataFrameRef("2026-07-15"),
                    new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"),
                    new JourneyPatternRef("FTR:JourneyPattern:59"));

    private static final Map<String, Integer> UIC_MAP = Map.of(
            "HKI", 1,
            "TPE", 160,
            "TKU", 130,
            "OL", 280
    );

    private static final Map<String, String> NAME_MAP = Map.of(
            "HKI", "Helsinki",
            "TPE", "Tampere",
            "TKU", "Turku",
            "OL", "Oulu"
    );

    private SiriEtService service;
    private SiriWritingService writingService;
    private JourneyRefResolver journeyRefResolver;
    private StationUicLookup stationUicLookup;
    private SiriStopResolver siriStopResolver;
    // Planned track per station short code; empty = no planned track known (no quay change). Populate in a test.
    private final Map<String, String> plannedTracks = new HashMap<>();

    @BeforeEach
    void setUp() {
        plannedTracks.clear();

        // given — stub JourneyRefResolver always resolves train 59
        journeyRefResolver = (trainNumber, date) -> {
            if (trainNumber == 59L) {
                return Optional.of(RESOLVED_59);
            }
            return Optional.empty();
        };

        // given — stub StationUicLookup from map
        stationUicLookup = shortCode -> {
            final Integer uic = UIC_MAP.get(shortCode);
            return uic != null ? OptionalInt.of(uic) : OptionalInt.empty();
        };

        // given — real SiriStopResolver with in-memory PetiStopSource
        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null, null, null),
                                new PetiQuay("FSR:Quay:HKI-8", "8", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null),
                                new PetiQuay("FSR:Quay:TPE-2", "2", null, null, null))),
                new PetiStop("FSR:StopPlace:TKU", 1000130, "Turku", true, null,
                        List.of(new PetiQuay("FSR:Quay:TKU-3", "3", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null, null, null)))
        );
        siriStopResolver = new SiriStopResolver(petiStopSource.getMatcher());

        writingService = new SiriWritingService();

        service = newService(
                shortCode -> Optional.ofNullable(NAME_MAP.get(shortCode)),
                (trainNumber, date, shortCode) -> Optional.ofNullable(plannedTracks.get(shortCode)));
    }

    private SiriEtService newService(final StationNameLookup stationNameLookup,
                                     final PlannedTrackLookup plannedTrackLookup) {
        return new SiriEtService(
                journeyRefResolver,
                stationUicLookup,
                siriStopResolver,
                stationNameLookup,
                plannedTrackLookup,
                writingService,
                PRODUCER_REF,
                DATA_SOURCE
        );
    }

    // ===== HELPERS =====

    private GTFSTrain createTrain(final long trainNumber, final boolean cancelled) {
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(trainNumber, DEPARTURE_DATE);
        train.cancelled = cancelled;
        train.timeTableRows = new ArrayList<>();
        return train;
    }

    private GTFSTimeTableRow createRow(final GTFSTrain train, final String stationShortCode,
                                       final TimeTableRow.TimeTableRowType type,
                                       final ZonedDateTime scheduledTime) {
        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.stationShortCode = stationShortCode;
        row.type = type;
        row.scheduledTime = scheduledTime;
        row.commercialStop = true;
        row.train = train;
        return row;
    }

    private void addStop(final GTFSTrain train, final String stationShortCode,
                         final ZonedDateTime arrivalTime, final ZonedDateTime departureTime,
                         final String track) {
        if (arrivalTime != null) {
            final GTFSTimeTableRow arrival = createRow(train, stationShortCode,
                    TimeTableRow.TimeTableRowType.ARRIVAL, arrivalTime);
            arrival.commercialTrack = track;
            train.timeTableRows.add(arrival);
        }
        if (departureTime != null) {
            final GTFSTimeTableRow departure = createRow(train, stationShortCode,
                    TimeTableRow.TimeTableRowType.DEPARTURE, departureTime);
            departure.commercialTrack = track;
            train.timeTableRows.add(departure);
        }
    }

    private GTFSTrain createStandard4StopTrain() {
        final GTFSTrain train = createTrain(59L, false);
        // Origin: DEPARTURE only
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        // Middle stops: ARRIVAL + DEPARTURE
        addStop(train, "TPE",
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI), "1");
        addStop(train, "TKU",
                ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 11, 5, 0, 0, HELSINKI), "3");
        // Terminus: ARRIVAL only
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        return train;
    }

    private List<EstimatedVehicleJourney> getEvjs(final Siri siri) {
        assertNotNull(siri.getServiceDelivery());
        final List<EstimatedTimetableDeliveryStructure> deliveries =
                siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        assertNotNull(deliveries);
        assertFalse(deliveries.isEmpty());
        final List<EstimatedVersionFrameStructure> frames =
                deliveries.get(0).getEstimatedJourneyVersionFrames();
        assertNotNull(frames);
        assertFalse(frames.isEmpty());
        return frames.get(0).getEstimatedVehicleJourneies();
    }

    /** EVJs tolerating an absent delivery/frame — an empty feed emits a bare delivery with no frame. */
    private List<EstimatedVehicleJourney> getEvjsOrEmpty(final Siri siri) {
        final List<EstimatedTimetableDeliveryStructure> deliveries =
                siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (deliveries == null || deliveries.isEmpty()) {
            return List.of();
        }
        final List<EstimatedVersionFrameStructure> frames =
                deliveries.get(0).getEstimatedJourneyVersionFrames();
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        return frames.get(0).getEstimatedVehicleJourneies();
    }

    // ===== AREA 1 — EVJ structure & journey ref =====

    // --- ET-01: Single running train → one EVJ in frame ---

    @Test
    void givenSingleRunningTrain_whenBuild_thenOneEvjInFrame() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final List<EstimatedVehicleJourney> evjs = getEvjs(result);
        assertEquals(1, evjs.size());
    }

    // --- ET-02: FramedVehicleJourneyRef has correct DataFrameRef and DatedVehicleJourneyRef ---

    @Test
    void givenTrain_whenBuild_thenFramedVehicleJourneyRefCorrect() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertNotNull(evj.getFramedVehicleJourneyRef());
        assertEquals("2026-07-15", evj.getFramedVehicleJourneyRef().getDataFrameRef().getValue());
        assertEquals("FTR:ServiceJourney:59-12345", evj.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    // --- ET-02b: JourneyPatternRef emitted from the resolved (persisted) journey ---

    @Test
    void givenTrain_whenBuild_thenJourneyPatternRefFromResolver() {
        final GTFSTrain train = createStandard4StopTrain();

        final Siri result = service.buildEtDocument(List.of(train), NOW);

        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertNotNull(evj.getJourneyPatternRef());
        assertEquals("FTR:JourneyPattern:59", evj.getJourneyPatternRef().getValue());
    }

    // --- ET-03: LineRef from resolver ---

    @Test
    void givenTrain_whenBuild_thenLineRefFromResolver() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertNotNull(evj.getLineRef());
        assertEquals("FTR:Line:IC", evj.getLineRef().getValue());
    }

    // --- ET-04: DataSource is configured codespace ---

    @Test
    void givenTrain_whenBuild_thenDataSourceIsConfigured() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals("FSR", evj.getDataSource());
    }

    // --- ET-05: DirectionRef always "0" ---

    @Test
    void givenTrain_whenBuild_thenDirectionRefIsZero() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertNotNull(evj.getDirectionRef());
        assertEquals("0", evj.getDirectionRef().getValue());
    }

    // ===== AREA 2 — Recorded/Estimated call split & ordering =====

    // --- ET-06: All stops future → all EstimatedCalls, no RecordedCalls ---

    @Test
    void givenAllFutureStops_whenBuild_thenAllEstimatedCalls() {
        // given — 3-stop train, no actualTime on any row
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), "7");
        addStop(train, "TPE",
                ZonedDateTime.of(2026, 7, 15, 15, 30, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 15, 35, 0, 0, HELSINKI), "1");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 18, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var recordedCalls = evj.getRecordedCalls();
        final var estimatedCalls = evj.getEstimatedCalls();
        assertTrue(recordedCalls == null || recordedCalls.getRecordedCalls().isEmpty());
        assertNotNull(estimatedCalls);
        assertEquals(3, estimatedCalls.getEstimatedCalls().size());
    }

    // --- ET-07: First two stops have actualTime → RecordedCalls, rest EstimatedCalls ---

    @Test
    void givenMixedActuals_whenBuild_thenRecordedAndEstimatedSplit() {
        // given — 4-stop train; stops 1-2 have arrival.actualTime
        final GTFSTrain train = createTrain(59L, false);
        // Origin
        final GTFSTimeTableRow dep1 = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        dep1.actualTime = ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        dep1.commercialTrack = "7";
        train.timeTableRows.add(dep1);
        // Stop 2
        final GTFSTimeTableRow arr2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI);
        arr2.commercialTrack = "1";
        train.timeTableRows.add(arr2);
        final GTFSTimeTableRow dep2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 36, 0, 0, HELSINKI);
        dep2.commercialTrack = "1";
        train.timeTableRows.add(dep2);
        // Stop 3 — no actuals
        addStop(train, "TKU",
                ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 11, 5, 0, 0, HELSINKI), "3");
        // Terminus — no actuals
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(2, evj.getRecordedCalls().getRecordedCalls().size());
        assertEquals(2, evj.getEstimatedCalls().getEstimatedCalls().size());
    }

    // --- ET-07b: A past stop with no actual (before the furthest actual) is a "missed" RecordedCall ---
    // It stays in RecordedCalls (chronological prefix) and carries the estimate as ExpectedArrivalTime, with
    // no ActualArrivalTime — the SIRI-2.0 way of signalling the actual is unavailable / the call was missed.
    @Test
    void givenPastStopWithoutActualBeforeAnActual_whenBuild_thenMissedRecordedCallWithExpectedTime() {
        final GTFSTrain train = createTrain(59L, false);
        // HKI origin — served (actual departure)
        final GTFSTimeTableRow dep1 = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        dep1.actualTime = ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        dep1.commercialTrack = "7";
        train.timeTableRows.add(dep1);
        // TPE middle — MISSED: no actual, only estimates
        final GTFSTimeTableRow arr2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr2.liveEstimateTime = ZonedDateTime.of(2026, 7, 15, 9, 33, 0, 0, HELSINKI);
        arr2.commercialTrack = "1";
        train.timeTableRows.add(arr2);
        final GTFSTimeTableRow dep2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep2.liveEstimateTime = ZonedDateTime.of(2026, 7, 15, 9, 38, 0, 0, HELSINKI);
        dep2.commercialTrack = "1";
        train.timeTableRows.add(dep2);
        // TKU middle — served (actuals) AFTER the missed TPE, which is what makes TPE a past/missed call
        final GTFSTimeTableRow arr3 = createRow(train, "TKU", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, HELSINKI));
        arr3.actualTime = ZonedDateTime.of(2026, 7, 15, 11, 2, 0, 0, HELSINKI);
        arr3.commercialTrack = "3";
        train.timeTableRows.add(arr3);
        final GTFSTimeTableRow dep3 = createRow(train, "TKU", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 11, 5, 0, 0, HELSINKI));
        dep3.actualTime = ZonedDateTime.of(2026, 7, 15, 11, 6, 0, 0, HELSINKI);
        dep3.commercialTrack = "3";
        train.timeTableRows.add(dep3);
        // OL terminus — upcoming
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        final Siri result = service.buildEtDocument(List.of(train), NOW);

        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final List<RecordedCall> recorded = evj.getRecordedCalls().getRecordedCalls();
        final List<EstimatedCall> estimated = evj.getEstimatedCalls().getEstimatedCalls();
        // HKI, TPE (missed) and TKU are all in the past; only OL is upcoming.
        assertEquals(3, recorded.size());
        assertEquals(1, estimated.size());
        // The split stays a continuous chronological prefix/suffix.
        assertEquals(2, recorded.get(1).getOrder().intValue());
        assertEquals(4, estimated.get(0).getOrder().intValue());
        // The missed TPE carries the estimate as Expected*Time, with no Actual*Time.
        final RecordedCall tpe = recorded.get(1);
        assertNull(tpe.getActualArrivalTime());
        assertNotNull(tpe.getExpectedArrivalTime());
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 33, 0, 0, HELSINKI).toInstant(),
                tpe.getExpectedArrivalTime().toInstant());
        assertNull(tpe.getActualDepartureTime());
        assertNotNull(tpe.getExpectedDepartureTime());
    }

    // --- ET-08: Continuous Order across Recorded and Estimated ---

    @Test
    void givenMixedCalls_whenBuild_thenContinuousOrderNumbers() {
        // given — same 4-stop train from ET-07
        final GTFSTrain train = createTrain(59L, false);
        final GTFSTimeTableRow dep1 = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        dep1.actualTime = ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        dep1.commercialTrack = "7";
        train.timeTableRows.add(dep1);
        final GTFSTimeTableRow arr2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI);
        arr2.commercialTrack = "1";
        train.timeTableRows.add(arr2);
        final GTFSTimeTableRow dep2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep2.commercialTrack = "1";
        train.timeTableRows.add(dep2);
        addStop(train, "TKU",
                ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 11, 5, 0, 0, HELSINKI), "3");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final List<RecordedCall> recorded = evj.getRecordedCalls().getRecordedCalls();
        final List<EstimatedCall> estimated = evj.getEstimatedCalls().getEstimatedCalls();
        assertEquals(1, recorded.get(0).getOrder().intValue());
        assertEquals(2, recorded.get(1).getOrder().intValue());
        assertEquals(3, estimated.get(0).getOrder().intValue());
        assertEquals(4, estimated.get(1).getOrder().intValue());
    }

    // --- ET-08b: timeTableRows in arbitrary order → calls emitted in schedule order ---

    @Test
    void givenUnorderedTimeTableRows_whenBuild_thenCallsInScheduleOrder() {
        // given — a 3-stop future train whose rows are added in scrambled order
        final GTFSTrain train = createTrain(59L, false);
        final GTFSTimeTableRow olArr = createRow(train, "OL", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI));
        olArr.commercialTrack = "1";
        final GTFSTimeTableRow hkiDep = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        hkiDep.commercialTrack = "7";
        final GTFSTimeTableRow tpeArr = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        tpeArr.commercialTrack = "1";
        final GTFSTimeTableRow tpeDep = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        tpeDep.commercialTrack = "1";
        train.timeTableRows.add(olArr);
        train.timeTableRows.add(tpeDep);
        train.timeTableRows.add(hkiDep);
        train.timeTableRows.add(tpeArr);

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — calls come out in schedule order HKI(1), TPE(2), OL(3)
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final List<EstimatedCall> calls = evj.getEstimatedCalls().getEstimatedCalls();
        assertEquals(3, calls.size());
        assertEquals("FSR:Quay:HKI-7", calls.get(0).getStopPointRef().getValue());
        assertEquals("FSR:Quay:TPE-1", calls.get(1).getStopPointRef().getValue());
        assertEquals("FSR:Quay:OL-1", calls.get(2).getStopPointRef().getValue());
        assertEquals(1, calls.get(0).getOrder().intValue());
        assertEquals(2, calls.get(1).getOrder().intValue());
        assertEquals(3, calls.get(2).getOrder().intValue());
    }

    // --- ET-09: Stop with arrival.actualTime but no departure.actualTime → still RecordedCall ---

    @Test
    void givenArrivalActualButNoDepartureActual_whenBuild_thenStillRecordedCall() {
        // given — stop 2 has arrival.actualTime set, departure.actualTime null
        final GTFSTrain train = createTrain(59L, false);
        final GTFSTimeTableRow dep1 = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        dep1.actualTime = ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        dep1.commercialTrack = "7";
        train.timeTableRows.add(dep1);
        // Stop 2: arrival has actualTime, departure does not
        final GTFSTimeTableRow arr2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI);
        arr2.commercialTrack = "1";
        train.timeTableRows.add(arr2);
        final GTFSTimeTableRow dep2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep2.liveEstimateTime = ZonedDateTime.of(2026, 7, 15, 9, 37, 0, 0, HELSINKI);
        dep2.commercialTrack = "1";
        train.timeTableRows.add(dep2);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final List<RecordedCall> recorded = evj.getRecordedCalls().getRecordedCalls();
        // Stop 2 is a RecordedCall (has arrival.actualTime)
        assertTrue(recorded.size() >= 2);
        final RecordedCall stop2 = recorded.get(1);
        assertNotNull(stop2.getExpectedDepartureTime());
    }

    // --- ET-10: All stops have actualTime → all RecordedCalls ---

    @Test
    void givenAllStopsWithActualTime_whenBuild_thenAllRecordedCalls() {
        // given — 3-stop train, all rows with actualTime
        final GTFSTrain train = createTrain(59L, false);
        final GTFSTimeTableRow dep1 = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        dep1.actualTime = ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        dep1.commercialTrack = "7";
        train.timeTableRows.add(dep1);
        final GTFSTimeTableRow arr2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI);
        arr2.commercialTrack = "1";
        train.timeTableRows.add(arr2);
        final GTFSTimeTableRow dep2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 36, 0, 0, HELSINKI);
        dep2.commercialTrack = "1";
        train.timeTableRows.add(dep2);
        final GTFSTimeTableRow arr3 = createRow(train, "OL", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI));
        arr3.actualTime = ZonedDateTime.of(2026, 7, 15, 14, 2, 0, 0, HELSINKI);
        arr3.commercialTrack = "1";
        train.timeTableRows.add(arr3);

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var estimatedCalls = evj.getEstimatedCalls();
        assertTrue(estimatedCalls == null || estimatedCalls.getEstimatedCalls().isEmpty());
        assertNotNull(evj.getRecordedCalls());
        assertEquals(3, evj.getRecordedCalls().getRecordedCalls().size());
    }

    // ===== AREA 3 — StopPointRef resolution =====

    // --- ET-11: Known track → Quay ref ---

    @Test
    void givenKnownTrack_whenBuild_thenStopPointRefIsQuay() {
        // given — stop at HKI, track "7"
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var calls = evj.getEstimatedCalls().getEstimatedCalls();
        assertEquals("FSR:Quay:HKI-7", calls.get(0).getStopPointRef().getValue());
    }

    // --- ET-12: Null track → no Quay → whole journey dropped ---

    @Test
    void givenNullTrack_whenBuild_thenJourneyDropped() {
        // given — stop at TPE, track null
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        // TPE with null track
        final GTFSTimeTableRow arr = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr.commercialTrack = null;
        train.timeTableRows.add(arr);
        final GTFSTimeTableRow dep = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep.commercialTrack = null;
        train.timeTableRows.add(dep);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — a stop with no resolvable Quay drops the whole journey
        assertTrue(getEvjsOrEmpty(result).isEmpty());
    }

    // --- ET-13: unknownTrack=true → no Quay → whole journey dropped ---

    @Test
    void givenUnknownTrackTrue_whenBuild_thenJourneyDropped() {
        // given — stop at TKU, track "3" but unknownTrack=true
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        final GTFSTimeTableRow arr = createRow(train, "TKU", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 11, 0, 0, 0, HELSINKI));
        arr.commercialTrack = "3";
        arr.unknownTrack = true;
        train.timeTableRows.add(arr);
        final GTFSTimeTableRow dep = createRow(train, "TKU", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 11, 5, 0, 0, HELSINKI));
        dep.commercialTrack = "3";
        dep.unknownTrack = true;
        train.timeTableRows.add(dep);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — unknownTrack leaves the stop without a Quay, so the whole journey is dropped
        assertTrue(getEvjsOrEmpty(result).isEmpty());
    }

    // --- ET-14: Station not in UIC lookup → whole journey omitted (complete-sequence rule) ---

    @Test
    void givenStationNotInUicLookup_whenBuild_thenJourneyOmitted() {
        // given — stop at "XXX" (not in UIC map)
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "XXX",
                ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 10, 5, 0, 0, HELSINKI), "1");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — an unresolvable stop means the sequence cannot be complete, so the journey is not emitted
        assertTrue(getEvjsOrEmpty(result).isEmpty());
    }

    // ===== AREA 4 — Time fields =====

    // --- ET-15: EstimatedCall has aimed + expected times ---

    @Test
    void givenFutureStopWithLiveEstimate_whenBuild_thenAimedAndExpectedTimesSet() {
        // given
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        final GTFSTimeTableRow arr = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr.liveEstimateTime = ZonedDateTime.of(2026, 7, 15, 9, 33, 0, 0, HELSINKI);
        arr.commercialTrack = "1";
        train.timeTableRows.add(arr);
        final GTFSTimeTableRow dep = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep.liveEstimateTime = ZonedDateTime.of(2026, 7, 15, 9, 38, 0, 0, HELSINKI);
        dep.commercialTrack = "1";
        train.timeTableRows.add(dep);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final EstimatedCall call = evj.getEstimatedCalls().getEstimatedCalls().get(1); // TPE
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI), call.getAimedArrivalTime());
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 33, 0, 0, HELSINKI), call.getExpectedArrivalTime());
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI), call.getAimedDepartureTime());
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 38, 0, 0, HELSINKI), call.getExpectedDepartureTime());
    }

    // --- ET-16: EstimatedCall with no liveEstimate → expectedTime is null ---

    @Test
    void givenFutureStopWithNoLiveEstimate_whenBuild_thenExpectedTimesNull() {
        // given
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "TPE",
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI), "1");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final EstimatedCall call = evj.getEstimatedCalls().getEstimatedCalls().get(1); // TPE
        assertNull(call.getExpectedArrivalTime());
        assertNull(call.getExpectedDepartureTime());
    }

    // --- ET-17: RecordedCall has actual times ---

    @Test
    void givenPastStopWithActualTimes_whenBuild_thenRecordedCallHasActuals() {
        // given
        final GTFSTrain train = createTrain(59L, false);
        final GTFSTimeTableRow dep1 = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        dep1.actualTime = ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        dep1.commercialTrack = "7";
        train.timeTableRows.add(dep1);
        final GTFSTimeTableRow arr2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI);
        arr2.commercialTrack = "1";
        train.timeTableRows.add(arr2);
        final GTFSTimeTableRow dep2 = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep2.actualTime = ZonedDateTime.of(2026, 7, 15, 9, 36, 0, 0, HELSINKI);
        dep2.commercialTrack = "1";
        train.timeTableRows.add(dep2);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final RecordedCall call = evj.getRecordedCalls().getRecordedCalls().get(1); // TPE
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI), call.getActualArrivalTime());
        assertEquals(ZonedDateTime.of(2026, 7, 15, 9, 36, 0, 0, HELSINKI), call.getActualDepartureTime());
    }

    // --- ET-18: Helsinki zone correctness — UTC source emits Helsinki local ---

    @Test
    void givenUtcScheduledTime_whenBuild_thenMarshalledXmlContainsHelsinkiLocal() {
        // given — scheduledTime in UTC (summer: Helsinki = UTC+3)
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneOffset.UTC), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, ZoneOffset.UTC), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);
        final String xml = writingService.marshalToXml(result);

        // then — UTC 10:00 → Helsinki 13:00 in summer
        assertTrue(xml.contains("2026-07-15T13:00:00"), "Expected Helsinki local time 13:00:00, got: " + xml);
    }

    // --- ET-19: Helsinki zone correctness — +02:00 winter source emits Helsinki local ---

    @Test
    void givenWinterOffsetTime_whenBuild_thenMarshalledXmlContainsHelsinkiLocal() {
        // given — scheduledTime at +02:00 (Helsinki winter = UTC+2, so same local)
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneId.of("+02:00")), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 1, 15, 14, 0, 0, 0, ZoneId.of("+02:00")), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);
        final String xml = writingService.marshalToXml(result);

        // then — +02:00 = Helsinki winter, so 10:00+02 → local 10:00
        assertTrue(xml.contains("2026-01-15T10:00:00"), "Expected Helsinki local time 10:00:00, got: " + xml);
    }

    // ===== AREA 5 — Cancellation =====

    // --- ET-20: Whole-journey cancellation ---

    @Test
    void givenCancelledTrain_whenBuild_thenEvjCancellationTrue() {
        // given
        final GTFSTrain train = createStandard4StopTrain();
        train.cancelled = true;

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(true, evj.isCancellation());
    }

    // --- ET-21: Non-cancelled train → cancellation not set ---

    @Test
    void givenNonCancelledTrain_whenBuild_thenEvjCancellationNullOrFalse() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertTrue(evj.isCancellation() == null || !evj.isCancellation());
    }

    // --- ET-22: Per-call cancellation ---

    @Test
    void givenOneStopCancelled_whenBuild_thenThatCallHasCancellationTrue() {
        // given — middle stop (TPE) rows are cancelled
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        final GTFSTimeTableRow arr = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr.commercialTrack = "1";
        arr.cancelled = true;
        train.timeTableRows.add(arr);
        final GTFSTimeTableRow dep = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep.commercialTrack = "1";
        dep.cancelled = true;
        train.timeTableRows.add(dep);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var calls = evj.getEstimatedCalls().getEstimatedCalls();
        // TPE is second call (index 1)
        assertEquals(true, calls.get(1).isCancellation());
        // Other calls are not cancelled
        assertTrue(calls.get(0).isCancellation() == null || !calls.get(0).isCancellation());
    }

    // ===== AREA 6 — First/last stop edge cases =====

    // --- ET-23: First stop (DEPARTURE only) → no aimed arrival time ---

    @Test
    void givenOriginStop_whenBuild_thenNoAimedArrivalTime() {
        // given
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var calls = evj.getEstimatedCalls().getEstimatedCalls();
        assertNull(calls.get(0).getAimedArrivalTime()); // origin has no arrival
        assertNotNull(calls.get(0).getAimedDepartureTime());
    }

    // --- ET-24: Last stop (ARRIVAL only) → no aimed departure time ---

    @Test
    void givenTerminusStop_whenBuild_thenNoAimedDepartureTime() {
        // given
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var calls = evj.getEstimatedCalls().getEstimatedCalls();
        final int lastIdx = calls.size() - 1;
        assertNotNull(calls.get(lastIdx).getAimedArrivalTime()); // terminus has arrival
        assertNull(calls.get(lastIdx).getAimedDepartureTime()); // no departure at terminus
    }

    // --- ET-25: Origin + terminus train (2 stops only) → 2 calls ---

    @Test
    void givenTwoStopTrain_whenBuild_thenTwoCalls() {
        // given — only origin (DEPARTURE) + terminus (ARRIVAL)
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(2, countAllCalls(evj));
    }

    // ===== AREA 7 — Filtering =====

    // --- ET-26: Non-commercial stops excluded ---

    @Test
    void givenNonCommercialMiddleStop_whenBuild_thenThatStopExcluded() {
        // given — 4-row train: middle stop has commercialStop=false
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        // Non-commercial stop
        final GTFSTimeTableRow arr = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr.commercialStop = false;
        arr.commercialTrack = "1";
        train.timeTableRows.add(arr);
        final GTFSTimeTableRow dep = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep.commercialStop = false;
        dep.commercialTrack = "1";
        train.timeTableRows.add(dep);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — should be 2 calls (HKI + OL), TPE excluded
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(2, countAllCalls(evj));
    }

    // --- ET-27: commercialStop=null treated as non-commercial ---

    @Test
    void givenNullCommercialStop_whenBuild_thenThatStopExcluded() {
        // given — middle stop has commercialStop=null on both rows
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        final GTFSTimeTableRow arr = createRow(train, "TPE", TimeTableRow.TimeTableRowType.ARRIVAL,
                ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI));
        arr.commercialStop = null;
        arr.commercialTrack = "1";
        train.timeTableRows.add(arr);
        final GTFSTimeTableRow dep = createRow(train, "TPE", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 9, 35, 0, 0, HELSINKI));
        dep.commercialStop = null;
        dep.commercialTrack = "1";
        train.timeTableRows.add(dep);
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — should be 2 calls (HKI + OL), TPE excluded
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(2, countAllCalls(evj));
    }

    // --- ET-28: Unresolvable journey → train skipped entirely ---

    @Test
    void givenUnresolvableJourney_whenBuild_thenTrainNotEmitted() {
        // given — train 999 is not resolvable by the stub JourneyRefResolver
        final GTFSTrain train = createTrain(999L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final List<EstimatedTimetableDeliveryStructure> deliveries =
                result.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (deliveries != null && !deliveries.isEmpty()) {
            final List<EstimatedVersionFrameStructure> frames =
                    deliveries.get(0).getEstimatedJourneyVersionFrames();
            if (frames != null && !frames.isEmpty()) {
                assertTrue(frames.get(0).getEstimatedVehicleJourneies().isEmpty());
            }
        }
        // alternatively: the entire delivery structure may be absent — also acceptable
    }

    // ===== AREA 8 — Multi-train & envelope =====

    // --- ET-29: Multiple trains → multiple EVJs in one frame ---

    @Test
    void givenMultipleTrains_whenBuild_thenMultipleEvjs() {
        // given — 2 trains, both resolvable (both trainNumber=59 for simplicity)
        final GTFSTrain train1 = createTrain(59L, false);
        addStop(train1, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train1, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        final GTFSTrain train2 = createTrain(59L, false);
        addStop(train2, "TPE", null,
                ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, HELSINKI), "1");
        addStop(train2, "TKU",
                ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, HELSINKI), null, "3");

        // when
        final Siri result = service.buildEtDocument(List.of(train1, train2), NOW);

        // then
        final List<EstimatedVehicleJourney> evjs = getEvjs(result);
        assertEquals(2, evjs.size());
    }

    // --- ET-30: One resolvable + one unresolvable → only resolvable emitted ---

    @Test
    void givenOneResolvableOneNot_whenBuild_thenOnlyResolvableEmitted() {
        // given
        final GTFSTrain trainA = createTrain(59L, false);
        addStop(trainA, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(trainA, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        final GTFSTrain trainB = createTrain(999L, false); // unresolvable
        addStop(trainB, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, HELSINKI), "7");
        addStop(trainB, "OL",
                ZonedDateTime.of(2026, 7, 15, 15, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(trainA, trainB), NOW);

        // then
        final List<EstimatedVehicleJourney> evjs = getEvjs(result);
        assertEquals(1, evjs.size());
    }

    // ===== AREA 9 — Schema validity =====

    // --- ET-31: Single-train document passes XSD validation ---

    @Test
    void givenStandardTrain_whenBuild_thenSchemaValid() {
        // given
        final GTFSTrain train = createStandard4StopTrain();
        // Add some actuals to get mixed Recorded/Estimated
        train.timeTableRows.get(0).actualTime =
                ZonedDateTime.of(2026, 7, 15, 8, 1, 0, 0, HELSINKI);
        train.timeTableRows.get(1).actualTime =
                ZonedDateTime.of(2026, 7, 15, 9, 32, 0, 0, HELSINKI);

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);
        final byte[] xml = writingService.marshalToBytes(result);

        // then
        assertTrue(writingService.isSchemaValid(xml));
    }

    // --- ET-32: Cancelled-train document passes XSD validation ---

    @Test
    void givenCancelledTrain_whenBuild_thenSchemaValid() {
        // given
        final GTFSTrain train = createStandard4StopTrain();
        train.cancelled = true;

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);
        final byte[] xml = writingService.marshalToBytes(result);

        // then
        assertTrue(writingService.isSchemaValid(xml));
    }

    // ===== AREA 10 — IsCompleteStopSequence flag =====

    // --- ET-33: All stops resolvable → IsCompleteStopSequence=true ---

    @Test
    void givenAllStopsResolvable_whenBuild_thenIsCompleteStopSequenceTrue() {
        // given
        final GTFSTrain train = createStandard4StopTrain();

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(true, evj.isIsCompleteStopSequence());
    }

    // --- ET-34: One stop unresolvable → whole journey omitted (never IsCompleteStopSequence=false) ---

    @Test
    void givenOneStopUnresolvable_whenBuild_thenJourneyOmitted() {
        // given — one station ("XXX") not in StationUicLookup
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "HKI", null,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "XXX",
                ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, HELSINKI),
                ZonedDateTime.of(2026, 7, 15, 10, 5, 0, 0, HELSINKI), "1");
        addStop(train, "OL",
                ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");

        // when
        final Siri result = service.buildEtDocument(List.of(train), NOW);

        // then — no journey is emitted; we never publish an incomplete sequence
        assertTrue(getEvjsOrEmpty(result).isEmpty());
    }

    @Test
    void givenScheduledTrain_whenBuild_thenVehicleModeOperatorRefAndNotMonitored() {
        final GTFSTrain train = createStandard4StopTrain();
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        assertEquals(1, evj.getVehicleModes().size());
        assertEquals(VehicleModesEnumeration.RAIL, evj.getVehicleModes().get(0));
        assertEquals("FTR:Operator:vr", evj.getOperatorRef().getValue());
        assertFalse(evj.isMonitored());
    }

    // --- ET-36: live estimate present → Monitored=true ---
    @Test
    void givenTrainWithLiveEstimate_whenBuild_thenMonitoredTrue() {
        final GTFSTrain train = createStandard4StopTrain();
        train.timeTableRows.get(5).liveEstimateTime =
                ZonedDateTime.of(2026, 7, 15, 14, 6, 0, 0, HELSINKI);
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        assertTrue(evj.isMonitored());
    }

    // --- ET-37: on-time train → onTime statuses + boarding/alighting + RequestStop=false ---
    @Test
    void givenOnTimeTrain_whenBuild_thenStatusesOnTimeAndBoardingSet() {
        final GTFSTrain train = createStandard4StopTrain();
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        final List<EstimatedCall> calls = evj.getEstimatedCalls().getEstimatedCalls();
        final EstimatedCall origin = calls.get(0);
        assertEquals(CallStatusEnumeration.ON_TIME, origin.getDepartureStatus());
        assertEquals(DepartureBoardingActivityEnumeration.BOARDING, origin.getDepartureBoardingActivity());
        assertEquals(Boolean.FALSE, origin.isRequestStop());
        final EstimatedCall mid = calls.get(1);
        assertEquals(CallStatusEnumeration.ON_TIME, mid.getArrivalStatus());
        assertEquals(ArrivalBoardingActivityEnumeration.ALIGHTING, mid.getArrivalBoardingActivity());
    }

    // --- ET-38: late departure → DepartureStatus=delayed ---
    @Test
    void givenLateDeparture_whenBuild_thenDepartureStatusDelayed() {
        final GTFSTrain train = createStandard4StopTrain();
        train.timeTableRows.get(2).liveEstimateTime = // TPE departure, 5 min late
                ZonedDateTime.of(2026, 7, 15, 9, 40, 0, 0, HELSINKI);
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        final EstimatedCall tpe = evj.getEstimatedCalls().getEstimatedCalls().get(1);
        assertEquals(CallStatusEnumeration.DELAYED, tpe.getDepartureStatus());
    }

    // --- ET-39: early arrival → ArrivalStatus=early ---
    @Test
    void givenEarlyArrival_whenBuild_thenArrivalStatusEarly() {
        final GTFSTrain train = createStandard4StopTrain();
        train.timeTableRows.get(3).liveEstimateTime = // TKU arrival, 2 min early
                ZonedDateTime.of(2026, 7, 15, 10, 58, 0, 0, HELSINKI);
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        final EstimatedCall tku = evj.getEstimatedCalls().getEstimatedCalls().get(2);
        assertEquals(CallStatusEnumeration.EARLY, tku.getArrivalStatus());
    }

    // --- ET-40: trailing stop cancelled → boundary DepartureStatus=cancelled + cancelled stop noAlighting ---
    @Test
    void givenTrailingStopCancelled_whenBuild_thenBoundaryAndCancelledStatuses() {
        final GTFSTrain train = createStandard4StopTrain();
        train.timeTableRows.get(5).cancelled = true; // cancel the terminus (OL arrival)
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        final List<EstimatedCall> calls = evj.getEstimatedCalls().getEstimatedCalls();
        final EstimatedCall tku = calls.get(2); // last served stop before the cancelled terminus
        assertEquals(CallStatusEnumeration.CANCELLED, tku.getDepartureStatus());
        assertEquals(DepartureBoardingActivityEnumeration.NO_BOARDING, tku.getDepartureBoardingActivity());
        final EstimatedCall ol = calls.get(3); // cancelled terminus
        assertEquals(Boolean.TRUE, ol.isCancellation());
        assertEquals(CallStatusEnumeration.CANCELLED, ol.getArrivalStatus());
        assertEquals(ArrivalBoardingActivityEnumeration.NO_ALIGHTING, ol.getArrivalBoardingActivity());
    }

    // --- ET-41: unknownDelay row → PredictionInaccurate=true on that call ---
    @Test
    void givenUnknownDelay_whenBuild_thenCallPredictionInaccurate() {
        final GTFSTrain train = createStandard4StopTrain();
        train.timeTableRows.get(2).unknownDelay = true; // TPE departure
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);
        final EstimatedCall tpe = evj.getEstimatedCalls().getEstimatedCalls().get(1);
        assertEquals(Boolean.TRUE, tpe.isPredictionInaccurate());
    }

    // ===== Group D — name fields (OriginName / DestinationName / StopPointName) =====

    @Test
    void givenNamedStations_whenBuild_thenOriginDestinationAndStopNamesEmitted() {
        final GTFSTrain train = createStandard4StopTrain(); // HKI, TPE, TKU, OL — all future → estimated calls
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);

        assertEquals("Helsinki", evj.getOriginNames().get(0).getValue());
        assertEquals("Oulu", evj.getDestinationNames().get(0).getValue());

        final List<EstimatedCall> calls = evj.getEstimatedCalls().getEstimatedCalls();
        assertEquals("Helsinki", calls.get(0).getStopPointNames().get(0).getValue());
        assertEquals("Tampere", calls.get(1).getStopPointNames().get(0).getValue());
        assertEquals("Turku", calls.get(2).getStopPointNames().get(0).getValue());
        assertEquals("Oulu", calls.get(3).getStopPointNames().get(0).getValue());
    }

    @Test
    void givenNameLookupMiss_whenBuild_thenNoNamesEmitted() {
        final SiriEtService noNames = newService(
                shortCode -> Optional.empty(),
                (trainNumber, date, shortCode) -> Optional.empty());
        final GTFSTrain train = createStandard4StopTrain();
        final EstimatedVehicleJourney evj = getEvjs(noNames.buildEtDocument(List.of(train), NOW)).get(0);

        assertTrue(evj.getOriginNames().isEmpty());
        assertTrue(evj.getDestinationNames().isEmpty());
        for (final EstimatedCall call : evj.getEstimatedCalls().getEstimatedCalls()) {
            assertTrue(call.getStopPointNames().isEmpty());
        }
    }

    // ===== Group C — platform (quay) change → ArrivalStopAssignment (valid on EstimatedCall in SIRI 2.0) =====

    // A genuine quay change is emitted as an ArrivalStopAssignment (aimed = planned quay, expected = actual quay);
    // StopPointRef always serves the actual (current) quay. StopAssignment is valid on an EstimatedCall in 2.0.
    @Test
    void givenPlannedTrackDiffersFromActual_whenBuild_thenArrivalStopAssignmentEmitted() {
        plannedTracks.put("TPE", "2"); // planned quay TPE-2; the train's actual TPE track is "1"
        final GTFSTrain train = createStandard4StopTrain();
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);

        final EstimatedCall tpe = evj.getEstimatedCalls().getEstimatedCalls().get(1);
        assertEquals(1, tpe.getArrivalStopAssignments().size());
        assertTrue(tpe.getDepartureStopAssignments().isEmpty());
        final StopAssignmentStructure assignment = tpe.getArrivalStopAssignments().get(0);
        assertEquals("FSR:Quay:TPE-2", assignment.getAimedQuayRef().getValue()); // planned
        assertEquals("FSR:Quay:TPE-1", assignment.getExpectedQuayRef().getValue()); // actual
        // StopPointRef stays the actual (current) quay.
        assertEquals("FSR:Quay:TPE-1", tpe.getStopPointRef().getValue());
    }

    @Test
    void givenPlannedTrackEqualsActual_whenBuild_thenNoStopAssignment() {
        plannedTracks.put("TPE", "1"); // same as the actual TPE track → no change
        final GTFSTrain train = createStandard4StopTrain();
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);

        final EstimatedCall tpe = evj.getEstimatedCalls().getEstimatedCalls().get(1);
        assertTrue(tpe.getArrivalStopAssignments().isEmpty());
        assertTrue(tpe.getDepartureStopAssignments().isEmpty());
    }

    @Test
    void givenNoPlannedTrack_whenBuild_thenNoStopAssignment() {
        final GTFSTrain train = createStandard4StopTrain(); // plannedTracks empty by default
        final EstimatedVehicleJourney evj = getEvjs(service.buildEtDocument(List.of(train), NOW)).get(0);

        for (final EstimatedCall call : evj.getEstimatedCalls().getEstimatedCalls()) {
            assertTrue(call.getArrivalStopAssignments().isEmpty());
            assertTrue(call.getDepartureStopAssignments().isEmpty());
        }
    }

    @Test
    void givenResolvableTrain_whenBuildWithStats_thenEmittedAndStopRefCounts() {
        final GTFSTrain train = createStandard4StopTrain(); // 4 stops, all future → estimated, all quays resolve
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(1, stats.journeysEmitted());
        assertEquals(0, stats.journeysCancelled());
        assertEquals(0, stats.skippedUnresolvedJourney());
        assertEquals(0, stats.skippedUnresolvedStopNoStop());
        assertEquals(0, stats.skippedUnresolvedStopNoQuay());
        assertEquals(4, stats.callsTotal());
        assertEquals(0, stats.callsRecorded());
        assertEquals(4, stats.callsEstimated());
        assertEquals(4, stats.stopRefsQuay());
        assertEquals(0, stats.stopRefsStopPlace());
        assertEquals(0, stats.stopRefsUnresolved());
        assertEquals(1.0, stats.matchRate().orElseThrow(), 0.0001);
    }

    @Test
    void givenMixedActuals_whenBuildWithStats_thenRecordedEstimatedSplit() {
        final GTFSTrain train = createStandard4StopTrain();
        train.timeTableRows.get(0).actualTime = ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI); // HKI dep
        train.timeTableRows.get(1).actualTime = ZonedDateTime.of(2026, 7, 15, 9, 30, 0, 0, HELSINKI); // TPE arr
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(2, stats.callsRecorded()); // HKI, TPE
        assertEquals(2, stats.callsEstimated()); // TKU, OL
        assertEquals(4, stats.callsTotal());
    }

    @Test
    void givenCancelledTrain_whenBuildWithStats_thenJourneyCancelledCounted() {
        final GTFSTrain train = createStandard4StopTrain();
        train.cancelled = true;
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(1, stats.journeysEmitted());
        assertEquals(1, stats.journeysCancelled());
    }

    @Test
    void givenUnresolvableJourney_whenBuildWithStats_thenSkippedUnresolvedJourney() {
        final GTFSTrain train = createTrain(999L, false); // resolver only knows train 59
        addStop(train, "HKI", null, ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "7");
        addStop(train, "OL", ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(0, stats.journeysEmitted());
        assertEquals(1, stats.skippedUnresolvedJourney());
        assertEquals(0, stats.skippedUnresolvedStopNoStop());
        assertEquals(0, stats.skippedUnresolvedStopNoQuay());
    }

    @Test
    void givenUnresolvableStop_whenBuildWithStats_thenSkippedNoStop() {
        final GTFSTrain train = createTrain(59L, false);
        addStop(train, "XXX", null, ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), "1"); // not in UIC map
        addStop(train, "OL", ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(0, stats.journeysEmitted());
        assertEquals(0, stats.skippedUnresolvedJourney());
        assertEquals(1, stats.skippedUnresolvedStopNoStop()); // XXX has no PETI stop place
        assertEquals(0, stats.skippedUnresolvedStopNoQuay());
        assertEquals(1, stats.stopRefsUnresolved());
    }

    @Test
    void givenUnknownTrack_whenBuildWithStats_thenSkippedNoQuay() {
        final GTFSTrain train = createTrain(59L, false);
        final GTFSTimeTableRow hkiDep = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI));
        hkiDep.unknownTrack = true; // HKI has a PETI stop place, but no known platform → no Quay → journey dropped
        train.timeTableRows.add(hkiDep);
        addStop(train, "OL", ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, HELSINKI), null, "1");
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(0, stats.journeysEmitted());
        assertEquals(0, stats.skippedUnresolvedStopNoStop());
        assertEquals(1, stats.skippedUnresolvedStopNoQuay()); // HKI stop place exists, platform/quay unknown
        assertEquals(1, stats.stopRefsUnresolved());
        assertEquals(0, stats.stopRefsStopPlace()); // no StopPlace refs are ever emitted
    }

    @Test
    void givenNoTrains_whenBuildWithStats_thenEmptyStatsAndNoMatchRate() {
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(), NOW).stats();

        assertEquals(0, stats.journeysEmitted());
        assertEquals(0, stats.callsTotal());
        assertTrue(stats.matchRate().isEmpty());
    }

    // ===== AREA — operating-day carryover (previous day's trains at the midnight boundary) =====

    // A yesterday-dated overnight train still en route (final stop not yet reached) is kept.
    @Test
    void givenYesterdayTrainStillRunning_whenBuildWithStats_thenEmitted() {
        final GTFSTrain train = overnightTrain(59L, DEPARTURE_DATE.minusDays(1),
                ZonedDateTime.of(2026, 7, 15, 13, 0, 0, 0, HELSINKI), null, null); // arrives 13:00, NOW is 12:00
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(1, stats.journeysEmitted());
        assertEquals(0, stats.skippedCompletedCarryover());
    }

    // A yesterday-dated train that already reached its terminus (final stop has an actual time) is dropped.
    @Test
    void givenYesterdayTrainCompleted_whenBuildWithStats_thenSkippedCarryover() {
        final GTFSTrain train = overnightTrain(59L, DEPARTURE_DATE.minusDays(1),
                ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, HELSINKI), null,
                ZonedDateTime.of(2026, 7, 15, 9, 5, 0, 0, HELSINKI)); // actual arrival → completed
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(0, stats.journeysEmitted());
        assertEquals(1, stats.skippedCompletedCarryover());
    }

    // A yesterday-dated train with no live data whose final stop time is well past is dropped as stale.
    @Test
    void givenYesterdayTrainStale_whenBuildWithStats_thenSkippedCarryover() {
        final GTFSTrain train = overnightTrain(59L, DEPARTURE_DATE.minusDays(1),
                ZonedDateTime.of(2026, 7, 14, 20, 0, 0, 0, HELSINKI), null, null); // scheduled far in the past
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(0, stats.journeysEmitted());
        assertEquals(1, stats.skippedCompletedCarryover());
    }

    // A today-dated train is always kept, even if all its stop times are already in the past.
    @Test
    void givenTodayTrainWithPastTimes_whenBuildWithStats_thenEmitted() {
        final GTFSTrain train = overnightTrain(59L, DEPARTURE_DATE,
                ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, HELSINKI), null, null); // 08:00, before NOW 12:00
        final SiriEtStats stats = service.buildEtDocumentWithStats(List.of(train), NOW).stats();

        assertEquals(1, stats.journeysEmitted());
        assertEquals(0, stats.skippedCompletedCarryover());
    }

    // A yesterday-dated (overnight) train carries its OWN departure date in FramedVehicleJourneyRef, not "today".
    @Test
    void givenOvernightTrain_whenBuild_thenFramedRefUsesTrainsOwnDate() {
        final LocalDate yesterday = DEPARTURE_DATE.minusDays(1);
        // Production PublishedJourneyRefResolver keys by (trainNumber, date); echo the date so we can assert it flows through.
        journeyRefResolver = (trainNumber, date) -> Optional.of(new ResolvedJourney(
                new ServiceJourneyId("FTR:ServiceJourney:59-" + date),
                new DataFrameRef(date.toString()),
                new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"),
                new JourneyPatternRef("FTR:JourneyPattern:59")));
        final SiriEtService svc = newService(
                shortCode -> Optional.ofNullable(NAME_MAP.get(shortCode)),
                (trainNumber, date, shortCode) -> Optional.empty());
        // Dated yesterday, still running (arrives today 13:00, NOW is today 12:00).
        final GTFSTrain train = overnightTrain(59L, yesterday,
                ZonedDateTime.of(2026, 7, 15, 13, 0, 0, 0, HELSINKI), null, null);

        final EstimatedVehicleJourney evj = getEvjs(svc.buildEtDocument(List.of(train), NOW)).get(0);

        assertEquals(yesterday.toString(),
                evj.getFramedVehicleJourneyRef().getDataFrameRef().getValue());
        assertEquals("FTR:ServiceJourney:59-" + yesterday,
                evj.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    private GTFSTrain createTrainOn(final long trainNumber, final LocalDate date) {
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(trainNumber, date);
        train.cancelled = false;
        train.timeTableRows = new ArrayList<>();
        return train;
    }

    /** Origin (HKI) → terminus (OL) train; the terminus arrival's scheduled/estimate/actual times are set explicitly. */
    private GTFSTrain overnightTrain(final long trainNumber, final LocalDate departureDate,
                                     final ZonedDateTime lastArrivalScheduled,
                                     final ZonedDateTime lastArrivalEstimate,
                                     final ZonedDateTime lastArrivalActual) {
        final GTFSTrain train = createTrainOn(trainNumber, departureDate);
        final GTFSTimeTableRow origin = createRow(train, "HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                lastArrivalScheduled.minusHours(5));
        origin.commercialTrack = "7";
        train.timeTableRows.add(origin);
        final GTFSTimeTableRow terminus = createRow(train, "OL", TimeTableRow.TimeTableRowType.ARRIVAL,
                lastArrivalScheduled);
        terminus.commercialTrack = "1";
        terminus.liveEstimateTime = lastArrivalEstimate;
        terminus.actualTime = lastArrivalActual;
        train.timeTableRows.add(terminus);
        return train;
    }

    // ===== Helper =====

    private int countAllCalls(final EstimatedVehicleJourney evj) {
        int count = 0;
        if (evj.getRecordedCalls() != null) {
            count += evj.getRecordedCalls().getRecordedCalls().size();
        }
        if (evj.getEstimatedCalls() != null) {
            count += evj.getEstimatedCalls().getEstimatedCalls().size();
        }
        return count;
    }
}
