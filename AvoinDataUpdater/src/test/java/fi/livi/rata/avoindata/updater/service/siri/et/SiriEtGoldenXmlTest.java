package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import uk.org.siri.siri21.Siri;

/**
 * Golden-master test: marshals deterministic SIRI-ET documents and compares each, node-for-node,
 * against a complete reference XML stored next to the test
 * ({@code src/test/resources/siri/expected-siri-et-{scenario}.xml}).
 *
 * <p>If the SIRI-ET mapping changes intentionally, regenerate the affected file(s) from
 * {@code target/siri-et-actual-{scenario}.xml} (written on every run) after confirming the new output is
 * correct.
 */
class SiriEtGoldenXmlTest {

    private static final ZoneId HELSINKI = SiriTimeConverter.HELSINKI_ZONE;
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, HELSINKI);
    private static final String PRODUCER_REF = "TEST";
    private static final String DATA_SOURCE = "FSR";

    private static final ResolvedJourney RESOLVED_59 =
            new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:59-12345"), new DataFrameRef("2026-07-15"),
                    new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"));

    private static final Map<String, Integer> UIC_MAP = Map.of(
            "HKI", 1,
            "TPE", 160,
            "TKU", 130,
            "OL", 280);

    private static final Map<String, String> NAME_MAP = Map.of(
            "HKI", "Helsinki",
            "TPE", "Tampere",
            "TKU", "Turku",
            "OL", "Oulu");

    // Planned tracks match the actual tracks the normal scenarios use, so only the quay-change scenario
    // (whose train departs from a different actual track) produces a StopAssignment.
    private static final Map<String, String> PLANNED_TRACKS = Map.of(
            "HKI", "7",
            "TPE", "1",
            "TKU", "3",
            "OL", "1");

    private SiriEtService service;
    private SiriWritingService writingService;

    @BeforeEach
    void setUp() {
        final JourneyRefResolver journeyRefResolver = (trainNumber, date) ->
                trainNumber == 59L ? Optional.of(RESOLVED_59) : Optional.empty();

        final StationUicLookup stationUicLookup = shortCode -> {
            final Integer uic = UIC_MAP.get(shortCode);
            return uic != null ? OptionalInt.of(uic) : OptionalInt.empty();
        };

        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null, null, null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null),
                                new PetiQuay("FSR:Quay:TPE-2", "2", null, null, null))),
                new PetiStop("FSR:StopPlace:TKU", 1000130, "Turku", true, null,
                        List.of(new PetiQuay("FSR:Quay:TKU-3", "3", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null, null, null))));

        final StationNameLookup stationNameLookup = shortCode -> Optional.ofNullable(NAME_MAP.get(shortCode));
        final PlannedTrackLookup plannedTrackLookup =
                (trainNumber, date, shortCode) -> Optional.ofNullable(PLANNED_TRACKS.get(shortCode));

        writingService = new SiriWritingService();
        service = new SiriEtService(
                journeyRefResolver,
                stationUicLookup,
                new SiriStopResolver(petiStopSource),
                stationNameLookup,
                plannedTrackLookup,
                writingService,
                PRODUCER_REF,
                DATA_SOURCE);
    }

    /** Each scenario builds a train whose serialized document has a structurally distinct shape. */
    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("mixed", (Supplier<GTFSTrain>) SiriEtGoldenXmlTest::mixedRecordedAndEstimatedTrain),
                Arguments.of("all-estimated", (Supplier<GTFSTrain>) SiriEtGoldenXmlTest::allEstimatedTrain),
                Arguments.of("cancelled", (Supplier<GTFSTrain>) SiriEtGoldenXmlTest::fullyCancelledTrain),
                Arguments.of("partial-cancellation",
                        (Supplier<GTFSTrain>) SiriEtGoldenXmlTest::partiallyCancelledTrain),
                Arguments.of("quay-change", (Supplier<GTFSTrain>) SiriEtGoldenXmlTest::quayChangeTrain));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void generatedSiriEt_matchesGoldenFile(final String scenario, final Supplier<GTFSTrain> trainBuilder)
            throws IOException {
        final Siri document = service.buildEtDocument(List.of(trainBuilder.get()), NOW);
        final String actual = writingService.marshalToXml(document);

        // Always dump the actual output so a diff / regeneration is trivial when the mapping changes.
        final Path dump = Path.of("target", "siri-et-actual-" + scenario + ".xml");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, actual, StandardCharsets.UTF_8);

        final String resource = "/siri/expected-siri-et-" + scenario + ".xml";
        final String expected = readGolden(resource);
        assertEquals(normalizeXml(expected), normalizeXml(actual),
                "SIRI-ET output differs from " + resource
                        + ". If the change is intentional, copy target/siri-et-actual-" + scenario
                        + ".xml over the resource.");
    }

    /**
     * A representative journey exercising the mixed mapping: an already-departed origin (RecordedCall), an
     * intermediate stop that has arrived but departs late (RecordedCall with an expected departure) and a
     * still-upcoming, delayed terminus (EstimatedCall). Produces both call containers.
     */
    private static GTFSTrain mixedRecordedAndEstimatedTrain() {
        final GTFSTrain train = train(false);

        final GTFSTimeTableRow hkiDep = row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, at(8, 0), "7");
        hkiDep.actualTime = at(8, 1);
        train.timeTableRows.add(hkiDep);

        final GTFSTimeTableRow tpeArr = row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, at(9, 30), "1");
        tpeArr.actualTime = at(9, 33);
        train.timeTableRows.add(tpeArr);
        final GTFSTimeTableRow tpeDep = row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, at(9, 35), "1");
        tpeDep.liveEstimateTime = at(9, 38);
        train.timeTableRows.add(tpeDep);

        final GTFSTimeTableRow olArr = row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, at(14, 0), "1");
        olArr.liveEstimateTime = at(14, 6);
        train.timeTableRows.add(olArr);

        return train;
    }

    /**
     * A fully upcoming journey with no actual times: every stop is an EstimatedCall, so the document has an
     * {@code EstimatedCalls} container and no {@code RecordedCalls} container. Live estimates make it monitored.
     */
    private static GTFSTrain allEstimatedTrain() {
        final GTFSTrain train = train(false);

        final GTFSTimeTableRow hkiDep = row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, at(13, 0), "7");
        hkiDep.liveEstimateTime = at(13, 2);
        train.timeTableRows.add(hkiDep);

        final GTFSTimeTableRow tpeArr = row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, at(14, 30), "1");
        tpeArr.liveEstimateTime = at(14, 35);
        train.timeTableRows.add(tpeArr);
        final GTFSTimeTableRow tpeDep = row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, at(14, 35), "1");
        tpeDep.liveEstimateTime = at(14, 40);
        train.timeTableRows.add(tpeDep);

        final GTFSTimeTableRow olArr = row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, at(18, 0), "1");
        olArr.liveEstimateTime = at(18, 10);
        train.timeTableRows.add(olArr);

        return train;
    }

    /**
     * A wholly cancelled journey: EVJ-level {@code Cancellation} plus every call cancelled (status CANCELLED,
     * no boarding). No actual times, so all calls are EstimatedCalls and the train is not monitored.
     */
    private static GTFSTrain fullyCancelledTrain() {
        final GTFSTrain train = train(true);

        final GTFSTimeTableRow hkiDep = row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, at(8, 0), "7");
        hkiDep.cancelled = true;
        train.timeTableRows.add(hkiDep);

        final GTFSTimeTableRow tpeArr = row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, at(9, 30), "1");
        tpeArr.cancelled = true;
        train.timeTableRows.add(tpeArr);
        final GTFSTimeTableRow tpeDep = row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, at(9, 35), "1");
        tpeDep.cancelled = true;
        train.timeTableRows.add(tpeDep);

        final GTFSTimeTableRow olArr = row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, at(14, 0), "1");
        olArr.cancelled = true;
        train.timeTableRows.add(olArr);

        return train;
    }

    /**
     * A partially cancelled journey: served origin and one served intermediate stop (RecordedCalls), then a
     * cancelled stop and cancelled terminus (EstimatedCalls with Cancellation). Exercises the boundary rule —
     * the last served stop departs {@code cancelled} because the next stop is cancelled. Produces both containers.
     */
    private static GTFSTrain partiallyCancelledTrain() {
        final GTFSTrain train = train(false);

        final GTFSTimeTableRow hkiDep = row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, at(8, 0), "7");
        hkiDep.actualTime = at(8, 0);
        train.timeTableRows.add(hkiDep);

        final GTFSTimeTableRow tpeArr = row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, at(9, 30), "1");
        tpeArr.actualTime = at(9, 31);
        train.timeTableRows.add(tpeArr);
        final GTFSTimeTableRow tpeDep = row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, at(9, 35), "1");
        tpeDep.actualTime = at(9, 35);
        train.timeTableRows.add(tpeDep);

        final GTFSTimeTableRow tkuArr = row("TKU", TimeTableRow.TimeTableRowType.ARRIVAL, at(11, 0), "3");
        tkuArr.cancelled = true;
        train.timeTableRows.add(tkuArr);
        final GTFSTimeTableRow tkuDep = row("TKU", TimeTableRow.TimeTableRowType.DEPARTURE, at(11, 5), "3");
        tkuDep.cancelled = true;
        train.timeTableRows.add(tkuDep);

        final GTFSTimeTableRow olArr = row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, at(14, 0), "1");
        olArr.cancelled = true;
        train.timeTableRows.add(olArr);

        return train;
    }

    /**
     * A fully upcoming journey whose intermediate stop uses a different actual track (TPE-2) than planned
     * (TPE-1), so its call carries a {@code StopAssignment} (aimed TPE-1, expected TPE-2). The other stops
     * keep their planned tracks → no assignment.
     */
    private static GTFSTrain quayChangeTrain() {
        final GTFSTrain train = train(false);

        final GTFSTimeTableRow hkiDep = row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE, at(13, 0), "7");
        hkiDep.liveEstimateTime = at(13, 0);
        train.timeTableRows.add(hkiDep);

        final GTFSTimeTableRow tpeArr = row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, at(14, 30), "2");
        tpeArr.liveEstimateTime = at(14, 30);
        train.timeTableRows.add(tpeArr);
        final GTFSTimeTableRow tpeDep = row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, at(14, 35), "2");
        tpeDep.liveEstimateTime = at(14, 35);
        train.timeTableRows.add(tpeDep);

        final GTFSTimeTableRow olArr = row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, at(18, 0), "1");
        olArr.liveEstimateTime = at(18, 0);
        train.timeTableRows.add(olArr);

        return train;
    }

    private static GTFSTrain train(final boolean cancelled) {
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(59L, DEPARTURE_DATE);
        train.cancelled = cancelled;
        train.timeTableRows = new ArrayList<>();
        return train;
    }

    private static GTFSTimeTableRow row(final String shortCode, final TimeTableRow.TimeTableRowType type,
            final ZonedDateTime scheduledTime, final String track) {
        final GTFSTimeTableRow r = new GTFSTimeTableRow();
        r.stationShortCode = shortCode;
        r.type = type;
        r.scheduledTime = scheduledTime;
        r.commercialTrack = track;
        r.commercialStop = true;
        return r;
    }

    private static ZonedDateTime at(final int hour, final int minute) {
        return ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, HELSINKI);
    }

    private static String readGolden(final String resource) throws IOException {
        try (InputStream in = SiriEtGoldenXmlTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing golden resource " + resource
                    + " — copy the matching target/siri-et-actual-*.xml there to create it.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Collapse insignificant whitespace between elements so formatting differences don't fail the test. */
    private static String normalizeXml(final String xml) {
        return xml.replaceAll("(?s)>\\s+<", "><").trim();
    }
}
