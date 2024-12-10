package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import fi.finrail.koju.model.KokoonpanoDto;
import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.updater.deserializers.JourneyCompositionConverter;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.CompositionPersistService;


@Service
public class CompositionInitializerService extends AbstractDatabaseInitializer<JourneyComposition> {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private CompositionPersistService compositionPersistService;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private JourneyCompositionConverter journeyCompositionConverter;

    private final RipaService.ETagRef latestVersionETag = new RipaService.ETagRef(null, log);

    @Override
    public String getPrefix() {
        return "julkisetkokoonpanot";
    }

    @Override
    public AbstractPersistService<JourneyComposition> getPersistService() {
        return compositionPersistService;
    }

    @Override
    protected Class<JourneyComposition[]> getEntityCollectionClass() {
        return JourneyComposition[].class;
    }

    @Override
    protected List<JourneyComposition> doUpdate() {
        final List<JourneyComposition> updatedCompositions = super.doUpdate();

        mqttPublish(updatedCompositions);

        return updatedCompositions;
    }

    /**
     *
     * @param path server path to query
     * @param responseType the return type of api (ignored for compositions custom implementation)
     * @param latestVersion version in koju-api is latestVersion-journeyCompositionVersionOffset
     * @return newest JourneyCompositions
     */
    @Override
    protected List<JourneyComposition> getObjectsNewerThanVersion(final String path, final Class<JourneyComposition[]> responseType,
                                                                  final long latestVersion) {
        // GET with timelimit:
        // NOTE! Koju-api only supports second accuracy
        // To be sure we get all data, we take 5 minutes to past from the newest messageDateTime ( == kokoonpano.getMessageDateTime().toInstant())
        // final Instant from = Instant.ofEpochMilli(latestMessageDateTime).minus(5, ChronoUnit.MINUTES).with(ChronoField.NANO_OF_SECOND, 0);
        // Period: P1M to get a month to future from latest messageDateTime. 1D was too short in case of longer downtime in stg env that caused hang in the update process
        // as there was no data in next 1D and etag did not change -> no data
        // final String targetPath = StringUtil.format("{}.json?time={}/P1M", path, usedMessageDateTime);

        // NOTE! This is not in use but for future reference!
        // GET with messageReference:
        // Use duration=-P1W and latestVersion-10 just to make sure to get latest changes
        // as the messages may not have always arrived in chronological order to koju
        try {
            final Instant messageDateTime = compositionPersistService.getMaxMessageDateTime();
            final Instant usedMessageDateTime = messageDateTime.minus(5, ChronoUnit.MINUTES).with(ChronoField.NANO_OF_SECOND, 0);
            final String targetPath = StringUtil.format("{}.json?time={}/P1M", path, usedMessageDateTime);
            final String usedEtag = latestVersionETag.getETag();
            log.info("method=getObjectsNewerThanVersion Fetching prefix={} from api={} with etag={} with original messageDateTime={} minus 5 min", this.prefix,
                    targetPath, usedEtag, messageDateTime);
            final KokoonpanoDto[] kokoonpanot =
                    ObjectUtils.getFirstNonNull(() -> ripaService.getFromKojuApiRestTemplate(targetPath, KokoonpanoDto[].class, latestVersionETag),
                            () -> new KokoonpanoDto[0]);
            // Api returns all versions/train after the version/time so filter only latest
            // null is returned when data is not modified
            final List<KokoonpanoDto> kokoonpanotNewest = journeyCompositionConverter.filterNewestVersions(kokoonpanot);
            final List<JourneyComposition> compositions = journeyCompositionConverter.transformToJourneyCompositions(kokoonpanotNewest);
            final Instant messageDateTimeNew = compositions.stream().map(c -> c.messageDateTime).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
            logTrainsPerDepartureDate("getObjectsNewerThanVersion", compositions);

            log.info(
                    "method=getObjectsNewerThanVersion prefix={} from api={} etag={} and updatedEtag={} returned originalCount={} compositions and after newest filter and " +
                            "deserialization count={} compositions with {} new, updated latest messageDateTime from messageDateTimeOld={} to messageDateTimeNew={}",
                    this.prefix, targetPath, usedEtag, latestVersionETag, kokoonpanot.length, kokoonpanotNewest.size(), compositions.size(),
                    messageDateTime,
                    messageDateTimeNew);
            return compositions;
        } catch (final Exception e) {
            log.error("method=getObjectsNewerThanVersion failed with {} reset latestVersionETag={}", e.getMessage(), latestVersionETag.getETag(), e);
            // Reset etag as we might got the right data but update failed so fetch data again
            latestVersionETag.reset();
            throw new RuntimeException(e);
        }
    }

    @Override
    protected <Type> Type[] getForObjectWithRetry(final String path, final Class<Type[]> responseType) {
        return retryTemplate.execute(context -> {
            log.info("Requesting data prefix={} from api={}", this.prefix, path);
            return ripaService.getFromKojuApiRestTemplate(path, responseType);
        });
    }

    @Override
    protected List<JourneyComposition> getForADay(final String path, final LocalDate localDate, final Class<JourneyComposition[]> type) {
        final String targetPath = String.format("%s/%s.json", path, localDate);
        final KokoonpanoDto[] kokoonpanot = ObjectUtils.getFirstNonNull(() -> getForObjectWithRetry(targetPath, KokoonpanoDto[].class), () -> new KokoonpanoDto[0]);
        // Api returns all versions/train so filter only latest for
        final  ArrayList<KokoonpanoDto> kokoonpanotNewest = journeyCompositionConverter.filterNewestVersions(kokoonpanot);
        final List<JourneyComposition> compositions = journeyCompositionConverter.transformToJourneyCompositions(kokoonpanotNewest);
        logTrainsPerDepartureDate("getForADay", compositions);
        log.info("method=getForADay prefix={} from api={} departureDate={} returned compositions originalCount={} and after newest filter and deserialization count={} compositions with {} new ones",
                this.prefix, targetPath, localDate, kokoonpanot.length, compositions.size(), kokoonpanotNewest.size() );
        return compositions;
    }

    private void mqttPublish(final List<JourneyComposition> updatedCompositions) {
        final List<JourneySection> journeySections = Lists.transform(updatedCompositions, s -> s.journeySection);
        final List<Composition> compositions = new ArrayList<>();
        for (final JourneySection journeySection : journeySections) {
            compositions.add(journeySection.composition);
        }

        try {
            mqttPublishService.publish(s -> String
                    .format("compositions/%s/%s/%s/%s/%s", s.id.departureDate, s.id.trainNumber, s.trainCategory, s.trainType,
                            s.operator.operatorShortCode), compositions);
        } catch (final Exception e) {
            log.error("method=mqttPublish Error publishing trains to MQTT", e);
        }
    }

    private void logTrainsPerDepartureDate(final String method, final List<JourneyComposition> compositions) {
        final Map<LocalDate, List<Long>> departureDateToTrains = compositions.stream()
                .collect(
                        Collectors.groupingBy(
                                s -> s.departureDate,
                                Collectors.mapping(
                                        s -> s.trainNumber,
                                        Collectors.toList())
                        )
                );
        departureDateToTrains.forEach(
                (key, value) -> log.info("method={} prefix={} update count={} trains for departureDate={} trainNumbers: {}", method, getPrefix(), value.size(), key, value));
    }
}
