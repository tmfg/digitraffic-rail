package fi.livi.rata.avoindata.updater.service.siri.vm;

import static fi.livi.rata.avoindata.common.utils.DateProvider.ZONE_ID_HKI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyEndpoint;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.StationNameLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.StationUicLookup;
import uk.org.siri.siri21.Siri;

/**
 * Golden-master test: marshals deterministic SIRI-VM documents and compares each, node-for-node, against a
 * complete reference XML stored next to the test ({@code src/test/resources/siri/expected-siri-vm-{scenario}.xml}).
 *
 * <p>If the SIRI-VM mapping changes intentionally, regenerate the affected file(s) from
 * {@code target/siri-vm-actual-{scenario}.xml} (written on every run) after confirming the new output is correct.
 */
class SiriVmGoldenXmlTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZONE_ID_HKI);
    private static final ZonedDateTime RECORDED_AT = ZonedDateTime.of(2026, 7, 15, 11, 59, 30, 0, ZONE_ID_HKI);
    private static final String PRODUCER_REF = "TEST";
    private static final String DATA_SOURCE = "FSR";

    private static final ResolvedJourney RESOLVED_59 =
            new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:59-12345"), new DataFrameRef("2026-07-15"),
                    new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"),
                    new JourneyPatternRef("FTR:JourneyPattern:59"),
                    new JourneyEndpoint("HKI", "1"), new JourneyEndpoint("OL", "3"));

    private SiriVmService service;
    private SiriWritingService writingService;

    @BeforeEach
    void setUp() {
        final JourneyRefResolver journeyRefResolver = (trainNumber, date) ->
                trainNumber == 59L ? Optional.of(RESOLVED_59) : Optional.empty();

        final StationUicLookup stationUicLookup = shortCode -> switch (shortCode) {
            case "TPE" -> OptionalInt.of(160);
            case "HKI" -> OptionalInt.of(1);
            case "OL" -> OptionalInt.of(650);
            default -> OptionalInt.empty();
        };

        final StationNameLookup stationNameLookup = shortCode -> switch (shortCode) {
            case "TPE" -> Optional.of("Tampere");
            case "HKI" -> Optional.of("Helsinki");
            case "OL" -> Optional.of("Oulu");
            default -> Optional.empty();
        };

        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-1", "1", null, null, null))),
                new PetiStop("FSR:StopPlace:OL", 1000650, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-3", "3", null, null, null))));

        writingService = new SiriWritingService();
        // None of these golden scenarios exercise the unknown-track planned-track fallback, so both lookups are
        // no-ops here; see VmJourneyConverterTest for fallback-specific coverage.
        final VmJourneyConverter converter = new VmJourneyConverter(
                journeyRefResolver, stationUicLookup, new SiriStopResolver(petiStopSource.getMatcher()),
                stationNameLookup, (trainNumber, departureDate, stationShortCode, visitIndex) -> Optional.empty(),
                (trainNumber, departureDate) -> List.of());
        final VmJourneyMarshaller marshaller = new VmJourneyMarshaller(writingService, PRODUCER_REF, DATA_SOURCE);
        service = new SiriVmService(converter, marshaller);
    }

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("minimum", (Supplier<GTFSTrainLocation>) SiriVmGoldenXmlTest::minimumLocation),
                Arguments.of("with-monitored-call",
                        (Supplier<GTFSTrainLocation>) SiriVmGoldenXmlTest::locationWithMonitoredCall),
                Arguments.of("at-stop", (Supplier<GTFSTrainLocation>) SiriVmGoldenXmlTest::locationAtStop));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void generatedSiriVm_matchesGoldenFile(final String scenario, final Supplier<GTFSTrainLocation> locationBuilder)
            throws IOException {
        final Siri document =
                service.buildVmDocumentWithStats(List.of(locationBuilder.get()), NOW).document();
        final String actual = writingService.marshalToXml(document);

        // Always dump the actual output so a diff / regeneration is trivial when the mapping changes.
        final Path dump = Path.of("target", "siri-vm-actual-" + scenario + ".xml");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, actual, StandardCharsets.UTF_8);

        final String resource = "/siri/expected-siri-vm-" + scenario + ".xml";
        final String expected = readGolden(resource);
        assertEquals(normalizeXml(expected), normalizeXml(actual),
                "SIRI-VM output differs from " + resource
                        + ". If the change is intentional, copy target/siri-vm-actual-" + scenario
                        + ".xml over the resource.");
    }

    /** A location with no station/track info: no {@code MonitoredCall} is emitted. */
    private static GTFSTrainLocation minimumLocation() {
        return new TestGTFSTrainLocation(1L, DEPARTURE_DATE, 59L, RECORDED_AT,
                24.938400, 60.169900, 90, 10, null, null, null, null, null);
    }

    /** A location whose next-stop station/track resolve to a PETI quay: a {@code MonitoredCall} is emitted,
     * together with a known real-time delay against that stop's scheduled time. The train is still approaching
     * the stop (not dwelling), so {@code VehicleAtStop} is {@code false}. */
    private static GTFSTrainLocation locationWithMonitoredCall() {
        return new TestGTFSTrainLocation(2L, DEPARTURE_DATE, 59L, RECORDED_AT,
                23.761000, 61.498100, 108, 10, "TPE", "1", false, 90, false);
    }

    /** Same resolved stop as {@link #locationWithMonitoredCall()}, but the train is currently dwelling there
     * ({@code VehicleAtStop = true}), so {@code VehicleLocationAtStop} is also emitted from the current fix. */
    private static GTFSTrainLocation locationAtStop() {
        return new TestGTFSTrainLocation(3L, DEPARTURE_DATE, 59L, RECORDED_AT,
                23.761000, 61.498100, 0, 10, "TPE", "1", false, 0, true);
    }

    private static String readGolden(final String resource) throws IOException {
        try (final InputStream in = SiriVmGoldenXmlTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing golden resource " + resource
                    + " — copy the matching target/siri-vm-actual-*.xml there to create it.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Collapse insignificant whitespace between elements so formatting differences don't fail the test. */
    private static String normalizeXml(final String xml) {
        return xml.replaceAll("(?s)>\\s+<", "><").trim();
    }
}
