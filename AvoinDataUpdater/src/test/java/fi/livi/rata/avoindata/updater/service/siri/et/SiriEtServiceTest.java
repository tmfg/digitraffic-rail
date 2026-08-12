package fi.livi.rata.avoindata.updater.service.siri.et;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
            new ResolvedJourney("DT:ServiceJourney:59-12345", "2026-07-15", "DT:Line:IC");

    private static final Map<String, Integer> UIC_MAP = Map.of(
            "HKI", 1,
            "TPE", 160,
            "TKU", 130,
            "OL", 280
    );

    private SiriEtService service;
    private SiriWritingService writingService;

    @BeforeEach
    void setUp() {
        // given — stub JourneyRefResolver always resolves train 59
        final JourneyRefResolver journeyRefResolver = (trainNumber, date) -> {
            if (trainNumber == 59L) {
                return Optional.of(RESOLVED_59);
            }
            return Optional.empty();
        };

        // given — stub StationUicLookup from map
        final StationUicLookup stationUicLookup = shortCode -> {
            final Integer uic = UIC_MAP.get(shortCode);
            return uic != null ? OptionalInt.of(uic) : OptionalInt.empty();
        };

        // given — real SiriStopResolver with in-memory PetiStopSource
        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null),
                                new PetiQuay("FSR:Quay:HKI-8", "8", null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null),
                                new PetiQuay("FSR:Quay:TPE-2", "2", null))),
                new PetiStop("FSR:StopPlace:TKU", 1000130, "Turku", true, null,
                        List.of(new PetiQuay("FSR:Quay:TKU-3", "3", null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null)))
        );
        final SiriStopResolver siriStopResolver = new SiriStopResolver(petiStopSource);

        writingService = new SiriWritingService();

        service = new SiriEtService(
                journeyRefResolver,
                stationUicLookup,
                siriStopResolver,
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
        assertEquals("DT:ServiceJourney:59-12345", evj.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
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
        assertEquals("DT:Line:IC", evj.getLineRef().getValue());
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

    // --- ET-12: Unknown/null track → StopPlace ref ---

    @Test
    void givenNullTrack_whenBuild_thenStopPointRefIsStopPlace() {
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

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var calls = evj.getEstimatedCalls().getEstimatedCalls();
        // TPE is second call (index 1)
        assertEquals("FSR:StopPlace:TPE", calls.get(1).getStopPointRef().getValue());
    }

    // --- ET-13: unknownTrack=true → StopPlace ref (track ignored) ---

    @Test
    void givenUnknownTrackTrue_whenBuild_thenStopPointRefIsStopPlace() {
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

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        final var calls = evj.getEstimatedCalls().getEstimatedCalls();
        // TKU is second call (index 1); should be StopPlace since unknownTrack=true
        assertEquals("FSR:StopPlace:TKU", calls.get(1).getStopPointRef().getValue());
    }

    // --- ET-14: Station not in UIC lookup → call omitted ---

    @Test
    void givenStationNotInUicLookup_whenBuild_thenCallOmittedAndIsCompleteStopSequenceFalse() {
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

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        // XXX should be skipped, so only 2 calls
        final int totalCalls = countAllCalls(evj);
        assertEquals(2, totalCalls);
        assertEquals(false, evj.isIsCompleteStopSequence());
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

    // --- ET-34: One stop unresolvable → IsCompleteStopSequence=false ---

    @Test
    void givenOneStopUnresolvable_whenBuild_thenIsCompleteStopSequenceFalse() {
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

        // then
        final EstimatedVehicleJourney evj = getEvjs(result).get(0);
        assertEquals(false, evj.isIsCompleteStopSequence());
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
