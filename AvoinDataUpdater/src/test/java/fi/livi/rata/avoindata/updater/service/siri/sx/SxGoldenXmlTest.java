package fi.livi.rata.avoindata.updater.service.siri.sx;

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
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage.MessageType;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageAudio;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageStation;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageVideo;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;

/**
 * Golden-master test: RAMI messages go through the converter and the marshaller, and the XML is compared with a
 * reference file ({@code src/test/resources/siri/expected-siri-sx-{scenario}.xml}).
 *
 * <p>If the SIRI-SX mapping changes on purpose, copy {@code target/siri-sx-actual-{scenario}.xml} (written on every
 * run) over the reference file after checking the new output.
 */
class SxGoldenXmlTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 27, 12, 0, 0, 0, ZONE_ID_HKI);
    private static final ZonedDateTime CREATED = NOW.minusHours(5);
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 10, 27);

    private final SiriWritingService writingService = new SiriWritingService();

    private final SxSituationConverter converter = new SxSituationConverter(
            code -> switch (code) {
                case "HKI" -> OptionalInt.of(1);
                case "PSL" -> OptionalInt.of(10);
                default -> OptionalInt.empty();
            },
            new SiriStopResolver(new PetiUicMatcher(List.of(
                    new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of()),
                    new PetiStop("FSR:StopPlace:10", 1_000_010, "Pasila", true, null, List.of())))),
            (trainNumber, date) -> trainNumber == 145
                    ? Optional.of(new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:145"),
                    new DataFrameRef("2026-10-27"), new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"),
                    null))
                    : Optional.empty());

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("station", (Supplier<RamiMessage>) SxGoldenXmlTest::stationMessage),
                Arguments.of("train", (Supplier<RamiMessage>) SxGoldenXmlTest::trainMessage),
                Arguments.of("closed", (Supplier<RamiMessage>) SxGoldenXmlTest::closedMessage));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void generatedSiriSx_matchesGoldenFile(final String scenario, final Supplier<RamiMessage> messageBuilder)
            throws IOException {
        final RamiMessage message = messageBuilder.get();
        final SxResult result = converter.convert(List.of(message), Map.of(message.id, CREATED));
        final SxSituationMarshaller marshaller = new SxSituationMarshaller(writingService, "FTR");
        final String actual = new String(
                marshaller.marshalToBytes(marshaller.marshal(result.situations(), NOW)), StandardCharsets.UTF_8);

        final Path dump = Path.of("target", "siri-sx-actual-" + scenario + ".xml");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, actual, StandardCharsets.UTF_8);

        final String resource = "/siri/expected-siri-sx-" + scenario + ".xml";
        assertEquals(normalizeXml(readGolden(resource)), normalizeXml(actual),
                "SIRI-SX output differs from " + resource + ". If the change is intentional, copy target/siri-sx-actual-"
                        + scenario + ".xml over the resource.");
    }

    /** Station message about two stations, with all three languages. The summary is cut, the description is not. */
    private static RamiMessage stationMessage() {
        final RamiMessage message = message("SHM20261027100000001", MessageType.SCHEDULED_MESSAGE);
        final RamiMessageVideo video = new RamiMessageVideo();
        video.id = 1L;
        video.textFi = "Raide 3 on suljettu huoltotöiden vuoksi. " + "Kulje raiteen 4 kautta. ".repeat(8).trim();
        video.textSv = "Spår 3 är avstängt.";
        video.textEn = "Platform 3 is closed.";
        message.videos.add(video);
        addStation(message, 1L, "PSL");
        addStation(message, 2L, "HKI");
        return message;
    }

    /** Train message with the spoken text only (no screen text). */
    private static RamiMessage trainMessage() {
        final RamiMessage message = message("MVM20261027100000002", MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE);
        message.trainNumber = 145;
        message.trainDepartureDate = DEPARTURE_DATE;
        final RamiMessageAudio audio = new RamiMessageAudio();
        audio.id = 1L;
        audio.textFi = "Junan vaunut 6 ja 7 puuttuvat.";
        message.audios.add(audio);
        addStation(message, 1L, "HKI");
        return message;
    }

    /** Deleted message: published as closed, ends 5 hours after the deletion. */
    private static RamiMessage closedMessage() {
        final RamiMessage message = message("SHM20261027100000003", MessageType.SCHEDULED_MESSAGE);
        message.deleted = NOW.minusHours(1);
        final RamiMessageVideo video = new RamiMessageVideo();
        video.id = 1L;
        video.textFi = "Hissi on poissa käytöstä.";
        message.videos.add(video);
        addStation(message, 1L, "HKI");
        return message;
    }

    private static RamiMessage message(final String id, final MessageType type) {
        final RamiMessage message = new RamiMessage();
        message.id = id;
        message.version = 2;
        message.messageType = type;
        message.createdSource = CREATED.plusHours(1);
        message.startValidity = CREATED;
        message.endValidity = NOW.plusDays(1);
        return message;
    }

    private static void addStation(final RamiMessage message, final long id, final String shortCode) {
        final RamiMessageStation station = new RamiMessageStation();
        station.id = id;
        station.stationShortCode = shortCode;
        message.stations.add(station);
    }

    private static String readGolden(final String resource) throws IOException {
        try (final InputStream in = SxGoldenXmlTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing golden resource " + resource
                    + " - copy the matching target/siri-sx-actual-*.xml there to create it.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Collapse whitespace between elements so formatting does not fail the test. */
    private static String normalizeXml(final String xml) {
        return xml.replaceAll("(?s)>\\s+<", "><").trim();
    }
}
