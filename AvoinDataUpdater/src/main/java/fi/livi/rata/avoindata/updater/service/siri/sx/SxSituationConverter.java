package fi.livi.rata.avoindata.updater.service.siri.sx;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageAudio;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageStation;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageVideo;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.StationUicLookup;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxReportType;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxSituation;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxTarget;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxText;

/**
 * Turns RAMI messages into situations. The rules are described in {@code docs/SIRI-SX.md}. The result is always
 * in the same order (by situation number) and has no values taken from the clock, so the same messages give
 * the same output.
 */
public class SxSituationConverter {

    /** A deleted message is published as closed for this many hours after it was deleted. */
    public static final int CLOSED_HOURS = 5;

    private final StationUicLookup stationUicLookup;
    private final SiriStopResolver stopResolver;
    private final JourneyRefResolver journeyRefResolver;

    public SxSituationConverter(final StationUicLookup stationUicLookup, final SiriStopResolver stopResolver,
                                final JourneyRefResolver journeyRefResolver) {
        this.stationUicLookup = stationUicLookup;
        this.stopResolver = stopResolver;
        this.journeyRefResolver = journeyRefResolver;
    }

    /**
     * @param ramiMessageFirstCreatedById creation time of the first version by message id; the creation time of the message
     *                     itself is used when an id is missing
     */
    public SxResult convert(final List<RamiMessage> messages, final Map<String, Instant> ramiMessageFirstCreatedById) {
        final List<SxSituation> situations = new ArrayList<>();
        final List<String> noJourneyIds = new ArrayList<>();
        int noText = 0;
        int nextStop = 0;
        int noStopPlace = 0;
        int unknownStations = 0;

        for (final RamiMessage message : messages) {
            final boolean trainMessage = message.isTrainMessage();
            final List<SxText> texts = texts(message);
            if (texts.isEmpty()) {
                noText++;
                continue;
            }
            if (trainMessage && texts.getFirst().isNextStop()) {
                nextStop++;
                continue;
            }

            final SxTarget target;
            if (trainMessage) {
                // The columns can be empty in the database, even though train messages normally have both.
                final Optional<ResolvedJourney> journey = !message.hasTrainDefined()
                        ? Optional.empty()
                        : journeyRefResolver.resolve(message.trainNumber, message.trainDepartureDate);
                if (journey.isEmpty()) {
                    noJourneyIds.add(message.id);
                    continue;
                }
                target = new SxTarget.Journey(journey.get().serviceJourneyId(), journey.get().dataFrameRef(),
                        journey.get().operatorRef(), journey.get().lineId());
            } else {
                // Keep this a TreeSet, not a HashSet: the order must always be the same. The generation compares the
                // new XML with the stored one and saves only when it differs, so a changing order would cause a new
                // save on every run. The same station can also be in the message twice.
                final TreeSet<String> stopPlaceIds = new TreeSet<>();
                for (final RamiMessageStation station : message.stations) {
                    final Optional<String> stopPlaceId = stopPlaceId(station.stationShortCode);
                    if (stopPlaceId.isPresent()) {
                        stopPlaceIds.add(stopPlaceId.get());
                    } else {
                        unknownStations++;
                    }
                }
                if (stopPlaceIds.isEmpty()) {
                    noStopPlace++;
                    continue;
                }
                target = new SxTarget.StopPlaces(List.copyOf(stopPlaceIds));
            }

            situations.add(situation(message, trainMessage, texts, target,
                    toHelsinki(ramiMessageFirstCreatedById.getOrDefault(message.id, message.createdSource))));
        }

        situations.sort(Comparator.comparing(SxSituation::situationNumber));
        noJourneyIds.sort(Comparator.naturalOrder());
        return new SxResult(situations, noText, nextStop, noStopPlace, unknownStations, noJourneyIds);
    }

    private static SxSituation situation(final RamiMessage message, final boolean trainMessage,
                                         final List<SxText> texts, final SxTarget target,
                                         final ZonedDateTime creationTime) {
        final boolean closed = message.deleted != null;
        final Instant startInstant = closed && message.deleted.isBefore(message.startValidity)
                ? message.deleted
                : message.startValidity;
        final Instant endInstant = closed
                ? message.deleted.plus(CLOSED_HOURS, ChronoUnit.HOURS)
                : message.endValidity;

        // RAMI ids (SHM..., MVM...) already differ from other id formats; add a source prefix if another source is added.
        return new SxSituation(
                NeTExIdGenerator.CODESPACE + ":SituationNumber:" + message.id,
                message.version,
                creationTime,
                toHelsinki(message.createdSource),
                closed,
                toHelsinki(startInstant),
                toHelsinki(endInstant),
                reportType(message),
                summaries(texts),
                descriptions(texts),
                target);
    }

    /**
     * Creates the Summary texts, shortening texts that exceed the SIRI-SX limit.
     */
    private static List<SxText> summaries(final List<SxText> texts) {
        return texts.stream().map(t -> new SxText(t.language(), SxTexts.summary(t.text()))).toList();
    }

    /**
     * Creates Description texts only for texts that were shortened for Summary.
     */
    private static List<SxText> descriptions(final List<SxText> texts) {
        return texts.stream()
                .filter(t -> !SxTexts.summary(t.text()).equals(t.text()))
                .toList();
    }

    private Optional<String> stopPlaceId(final String stationShortCode) {
        final OptionalInt uic = stationUicLookup.uicFor(stationShortCode);
        return uic.isPresent() ? stopResolver.resolveStopPlaceId(uic.getAsInt()) : Optional.empty();
    }

    /** Screen text per language, spoken text when a language has no screen text. Order: fi, sv, en. */
    private static List<SxText> texts(final RamiMessage message) {
        final List<RamiMessageVideo> videos = message.videos.stream().sorted(Comparator.comparing(v -> v.id)).toList();
        final List<RamiMessageAudio> audios = message.audios.stream().sorted(Comparator.comparing(a -> a.id)).toList();
        final List<SxText> result = new ArrayList<>();
        addText(result, "fi", videos.stream().map(v -> v.textFi).toList(), audios.stream().map(a -> a.textFi).toList());
        addText(result, "sv", videos.stream().map(v -> v.textSv).toList(), audios.stream().map(a -> a.textSv).toList());
        addText(result, "en", videos.stream().map(v -> v.textEn).toList(), audios.stream().map(a -> a.textEn).toList());
        return result;
    }

    /**
     * Adds the text of one language: the first non-blank screen text, or the first non-blank spoken text when the
     * language has no screen text. Nothing is added when both are missing.
     */
    private static void addText(final List<SxText> result, final String language, final List<String> screen,
                                final List<String> spoken) {
        screen.stream().filter(StringUtils::isNotBlank).findFirst()
                .or(() -> spoken.stream().filter(StringUtils::isNotBlank).findFirst())
                .ifPresent(text -> result.add(new SxText(language, text.strip())));
    }

    /**
     * RAMI has no field for the report type, and the message texts cannot be sorted reliably into incidents and
     * general information, so every message is general. Change here if a better rule is found.
     */
    private static SxReportType reportType(final RamiMessage message) {
        return SxReportType.GENERAL;
    }

    private static ZonedDateTime toHelsinki(final Instant instant) {
        return instant == null ? null : instant.atZone(DateProvider.ZONE_ID_HKI);
    }
}
