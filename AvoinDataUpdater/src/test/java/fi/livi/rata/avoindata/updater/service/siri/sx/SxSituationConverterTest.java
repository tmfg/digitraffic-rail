package fi.livi.rata.avoindata.updater.service.siri.sx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage.MessageType;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageAudio;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageStation;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageVideo;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxReportType;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxSituation;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxTarget;

public class SxSituationConverterTest {

    private static final ZonedDateTime CREATED = ZonedDateTime.of(2026, 10, 27, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.of(2026, 10, 27);

    private final SxSituationConverter converter = new SxSituationConverter(
            code -> switch (code) {
                case "HKI" -> OptionalInt.of(1);
                case "PSL" -> OptionalInt.of(10);
                case "OLLI" -> OptionalInt.of(999);
                default -> OptionalInt.empty();
            },
            new SiriStopResolver(new PetiUicMatcher(List.of(
                    new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of()),
                    new PetiStop("FSR:StopPlace:10", 1_000_010, "Pasila", true, null, List.of())))),
            (trainNumber, date) -> trainNumber == 145
                    ? Optional.of(new ResolvedJourney(new ServiceJourneyId("FTR:ServiceJourney:145"),
                    new DataFrameRef("2026-10-27"), new LineId("FTR:Line:IC"), new OperatorRef("FTR:Operator:vr"),
                    new JourneyPatternRef("FTR:JourneyPattern:1")))
                    : Optional.empty());

    private static RamiMessage message(final String id, final MessageType type, final String screenFi,
                                       final String spokenFi, final String... stations) {
        final RamiMessage message = new RamiMessage();
        message.id = id;
        message.version = 2;
        message.messageType = type;
        message.createdSource = CREATED.plusHours(1);
        message.startValidity = CREATED;
        message.endValidity = CREATED.plusDays(1);
        if (type == MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE) {
            message.trainNumber = 145;
            message.trainDepartureDate = DATE;
        }
        if (screenFi != null) {
            final RamiMessageVideo video = new RamiMessageVideo();
            video.id = 1L;
            video.textFi = screenFi;
            message.videos.add(video);
        }
        if (spokenFi != null) {
            final RamiMessageAudio audio = new RamiMessageAudio();
            audio.id = 1L;
            audio.textFi = spokenFi;
            message.audios.add(audio);
        }
        long stationId = 1;
        for (final String code : stations) {
            final RamiMessageStation station = new RamiMessageStation();
            station.id = stationId++;
            station.stationShortCode = code;
            message.stations.add(station);
        }
        return message;
    }

    private SxResult convert(final RamiMessage... messages) {
        return converter.convert(List.of(messages), Map.of("SHM1", CREATED.minusHours(1)));
    }

    @Test
    public void stationMessageTargetsStopPlacesOfItsStations() {
        final SxResult result = convert(message("SHM1", MessageType.SCHEDULED_MESSAGE, "Raide 3 suljettu", null,
                "PSL", "HKI", "HKI"));

        assertEquals(1, result.situations().size());
        final SxSituation situation = result.situations().getFirst();
        assertEquals("FTR:SituationNumber:SHM1", situation.situationNumber());
        assertEquals(2, situation.version());
        assertEquals(SxReportType.GENERAL, situation.reportType());
        assertEquals(CREATED.minusHours(1), situation.creationTime());
        assertEquals(CREATED.plusHours(1), situation.versionedAtTime());
        assertEquals(CREATED.plusDays(1), situation.endTime());
        assertEquals(new SxTarget.StopPlaces(List.of("FSR:StopPlace:1", "FSR:StopPlace:10")), situation.target());
    }

    @Test
    public void unknownStationsAreCountedAndMessageWithoutAnyKnownStationIsDropped() {
        final SxResult result = convert(
                message("SHM1", MessageType.SCHEDULED_MESSAGE, "Teksti", null, "HKI", "OLLI"),
                message("SHM2", MessageType.SCHEDULED_MESSAGE, "Teksti", null, "OLLI", "XXX"));

        assertEquals(List.of("FTR:SituationNumber:SHM1"),
                result.situations().stream().map(SxSituation::situationNumber).toList());
        assertEquals(1, result.noStopPlace());
        assertEquals(3, result.unknownStations());
    }

    @Test
    public void trainMessageTargetsJourneyWithOperatorAndLine() {
        final SxResult result = convert(message("MVM1", MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE,
                "Vaunut 6 ja 7 puuttuvat", null, "HKI"));

        final SxSituation situation = result.situations().getFirst();
        assertEquals(SxReportType.INCIDENT, situation.reportType());
        assertEquals(new SxTarget.Journey(new ServiceJourneyId("FTR:ServiceJourney:145"), new DataFrameRef("2026-10-27"),
                new OperatorRef("FTR:Operator:vr"), new LineId("FTR:Line:IC")), situation.target());
    }

    @Test
    public void trainMessageWithoutPublishedJourneyIsDroppedAndListed() {
        final RamiMessage unknown = message("MVM2", MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE, "Teksti", null, "HKI");
        unknown.trainNumber = 999;

        final SxResult result = convert(unknown);

        assertTrue(result.situations().isEmpty());
        assertEquals(List.of("MVM2"), result.noJourneyIds());
    }

    @Test
    public void trainMessageWithoutTrainNumberOrDateIsDroppedAndListed() {
        final RamiMessage noNumber = message("MVM3", MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE, "Teksti", null, "HKI");
        noNumber.trainNumber = null;
        final RamiMessage noDate = message("MVM4", MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE, "Teksti", null, "HKI");
        noDate.trainDepartureDate = null;

        final SxResult result = convert(noNumber, noDate);

        assertTrue(result.situations().isEmpty());
        assertEquals(List.of("MVM3", "MVM4"), result.noJourneyIds());
    }

    @Test
    public void nextStopMessageOfTrainIsLeftOutButStationMessageIsNot() {
        final SxResult result = convert(
                message("MVM1", MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE, "Seuraava pysähdys: Tampere", null, "HKI"),
                message("SHM1", MessageType.SCHEDULED_MESSAGE, "Seuraava pysähdys on tauolla", null, "HKI"));

        assertEquals(1, result.nextStop());
        assertEquals(List.of("FTR:SituationNumber:SHM1"),
                result.situations().stream().map(SxSituation::situationNumber).toList());
    }

    @Test
    public void spokenTextIsUsedWhenThereIsNoScreenTextAndMessageWithoutTextIsDropped() {
        final SxResult result = convert(
                message("SHM1", MessageType.SCHEDULED_MESSAGE, null, "Vain puhuttu", "HKI"),
                message("SHM2", MessageType.SCHEDULED_MESSAGE, "  ", null, "HKI"));

        assertEquals("Vain puhuttu", result.situations().getFirst().descriptions().getFirst().text());
        assertEquals(1, result.noText());
    }

    @Test
    public void longTextGivesCutSummaryAndFullDescription() {
        final String text = "sana ".repeat(60).trim();

        final SxSituation situation = convert(message("SHM1", MessageType.SCHEDULED_MESSAGE, text, null, "HKI"))
                .situations().getFirst();

        assertEquals(text, situation.descriptions().getFirst().text());
        assertTrue(situation.summaries().getFirst().text().length() <= 160);
        assertTrue(situation.summaries().getFirst().text().endsWith("..."));
        assertEquals("fi", situation.summaries().getFirst().language());
    }

    @Test
    public void deletedMessageIsClosedUntilFiveHoursAfterDeletion() {
        final RamiMessage deleted = message("SHM1", MessageType.SCHEDULED_MESSAGE, "Teksti", null, "HKI");
        deleted.deleted = CREATED.plusHours(2);

        final SxSituation situation = convert(deleted).situations().getFirst();

        assertTrue(situation.closed());
        assertEquals(CREATED.plusHours(7), situation.endTime());
    }

    @Test
    public void situationsAreSortedByNumber() {
        final SxResult result = convert(
                message("SHM3", MessageType.SCHEDULED_MESSAGE, "c", null, "HKI"),
                message("SHM1", MessageType.SCHEDULED_MESSAGE, "a", null, "HKI"),
                message("SHM2", MessageType.SCHEDULED_MESSAGE, "b", null, "HKI"));

        assertEquals(List.of("FTR:SituationNumber:SHM1", "FTR:SituationNumber:SHM2",
                        "FTR:SituationNumber:SHM3"),
                result.situations().stream().map(SxSituation::situationNumber).toList());
    }
}
