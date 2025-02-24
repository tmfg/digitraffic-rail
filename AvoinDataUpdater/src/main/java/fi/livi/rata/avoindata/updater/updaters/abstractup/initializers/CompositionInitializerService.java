package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import static fi.livi.rata.avoindata.updater.deserializers.JourneyCompositionConverter.getKeyForKokoonpano;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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

    private final Map<String, KokoonpanoDto> failedCompositionsInMemory =
            Collections.synchronizedMap(new PassiveExpiringMap<>(Duration.ofHours(1).toMillis()) {
                // Only called when removing expired objects from map.
                // We clean map by iterating and this is not called from iterator, only by PassiveExpiringMap when expiring value
                @Override
                public KokoonpanoDto remove(final Object key) {
                    final KokoonpanoDto value = super.remove(key);
                    if (value != null) { // null if already removed by iterator
                        log.error("method=failedCompositionsInMemoryExpired Removing failed composition in memory as it expired trainKey={}", key);
                    }
                    return value;
                }
            } );

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
     * @param path          server path to query
     * @param responseType  the return type of api (ignored for compositions custom implementation)
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
            log.info("method=getObjectsNewerThanVersion Fetching prefix={} from api={} with etag={} with original messageDateTime={} minus 5 min",
                    this.prefix,
                    targetPath, usedEtag, messageDateTime);
            final KokoonpanoDto[] kokoonpanot =
                    ObjectUtils.getFirstNonNull(() -> ripaService.getFromKojuApiRestTemplate(targetPath, KokoonpanoDto[].class, latestVersionETag),
                            () -> new KokoonpanoDto[0]);

            final List<KokoonpanoDto> kokoonpanoDtos = combineCompositionsWithFailedCompositionsInMemory(kokoonpanot);

            // Api returns all versions/train after the version/time so filter only latest
            // null is returned when data is not modified
            final List<KokoonpanoDto> kokoonpanotNewest = journeyCompositionConverter.filterNewestVersions(kokoonpanoDtos);
            final Pair<List<JourneyComposition>, List<KokoonpanoDto>> result =
                    journeyCompositionConverter.transformToJourneyCompositions(kokoonpanotNewest);
            final List<JourneyComposition> compositions = result.getLeft();
            // Update failed compositions to memory
            final Triple<Integer, Integer, Integer> failedInfo = updateFailedCompositionsInMemory(result.getRight());

            final Instant messageDateTimeNew =
                    compositions.stream().map(c -> c.messageDateTime).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
            logTrainsPerDepartureDate("getObjectsNewerThanVersion", compositions);

            log.info(
                    "method=getObjectsNewerThanVersion prefix={} from api={} etag={} and updatedEtag={} returned originalCount={} compositions and after newest filter and " +
                            "deserialization count={} compositions with {} new, updated latest messageDateTime from messageDateTimeOld={} to messageDateTimeNew={} " +
                            "failedCompositionsAddedToMemory={} failedCompositionsRemovedFromMemory={} failedCompositionsInMemory={}",
                    this.prefix, targetPath, usedEtag, latestVersionETag, kokoonpanot.length, kokoonpanotNewest.size(), compositions.size(),
                    messageDateTime,
                    messageDateTimeNew, failedInfo.getLeft(), failedInfo.getMiddle(), failedInfo.getRight());
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
        final KokoonpanoDto[] kokoonpanot =
                ObjectUtils.getFirstNonNull(() -> getForObjectWithRetry(targetPath, KokoonpanoDto[].class), () -> new KokoonpanoDto[0]);

        // Api returns all versions/train so filter only latest for
        final ArrayList<KokoonpanoDto> kokoonpanotNewest =
                journeyCompositionConverter.filterNewestVersions(new ArrayList<>(Arrays.asList(kokoonpanot)));

        final Pair<List<JourneyComposition>, List<KokoonpanoDto>> result =
                journeyCompositionConverter.transformToJourneyCompositions(kokoonpanotNewest);
        final List<JourneyComposition> compositions = result.getLeft();

        addFailedCompositionsToMemory(result.getRight());

        logTrainsPerDepartureDate("getForADay", compositions);
        log.info(
                "method=getForADay prefix={} from api={} departureDate={} returned compositions originalCount={} and after newest filter and deserialization count={} compositions with {} new ones",
                this.prefix, targetPath, localDate, kokoonpanot.length, compositions.size(), kokoonpanotNewest.size());
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
                (key, value) -> log.info("method={} prefix={} update count={} trains for departureDate={} trainNumbers: {}", method, getPrefix(),
                        value.size(), key, value));
    }

    /**
     * Returns given compositions with failed compositions fom memory so they will be also tried to update.
     *
     * @param compositions current list of compositions
     */
    private List<KokoonpanoDto> combineCompositionsWithFailedCompositionsInMemory(final KokoonpanoDto[] compositions) {
        final ArrayList<KokoonpanoDto> all = new ArrayList<>(Arrays.asList(compositions));
        all.addAll(failedCompositionsInMemory.values());
        return all;
    }

    /**
     * Add given failed compositions in memory
     * If train is already registered on the failed ones, then update it only if we have newer to add.
     *
     * @param failedCompositions failed compositions to add to memory.
     * @return keys added failed compositions in format "departureDate-trainNumber"
     */
    private Set<String> addFailedCompositionsToMemory(final List<KokoonpanoDto> failedCompositions) {
        final HashSet<String> addedKeys = new HashSet<>();
        if (!failedCompositions.isEmpty()) {
            failedCompositions.forEach(kokoonpano -> {
                final String key = getKeyForKokoonpano(kokoonpano);
                final KokoonpanoDto previousInMemory = failedCompositionsInMemory.get(key);
                // Only put if it has changed as put will reset expiration
                if (previousInMemory == null || previousInMemory.getMessageDateTime().isBefore(kokoonpano.getMessageDateTime())) {
                    failedCompositionsInMemory.put(key, kokoonpano);
                    addedKeys.add(key);
                }
            });
        }
        return addedKeys;
    }

    /**
     * Update failed compositions in memory with latest failed ones and remove ones not listed as failed.
     * If train is already registered on the failed ones, then update it only if we have newer to add.
     *
     * @param failedCompositions failed ones on last update to add to memory.
     * @return Sum of added, removed and current size of failed compositions in memory
     */
    private Triple<Integer, Integer, Integer> updateFailedCompositionsInMemory(final List<KokoonpanoDto> failedCompositions) {
        // First remove compositions from memory that has not failed anymore
        final HashSet<String> fixedKeys = new HashSet<>();
        final Set<String> failedKeys =
                failedCompositions.stream().map(JourneyCompositionConverter::getKeyForKokoonpano).collect(Collectors.toSet());
        final Iterator<String> iter = failedCompositionsInMemory.keySet().iterator();
        while (iter.hasNext()) {
            final String existingKeyInMemory = iter.next();
            if (!failedKeys.contains(existingKeyInMemory)) {
                iter.remove();
                fixedKeys.add(existingKeyInMemory);
            }
        }

        // Then add failed to memory
        final Set<String> addedKeys = addFailedCompositionsToMemory(failedCompositions);
        if (!addedKeys.isEmpty() || !fixedKeys.isEmpty()) {
            log.info(
                    "method=updateFailedCompositionsInMemory addedKeys={} addedKeysSize={} fixedKeys={} fixedKeysSize={} keysInMemory={} keysInMemorySize={}",
                    addedKeys, addedKeys.size(), fixedKeys, fixedKeys.size(),
                    failedCompositionsInMemory.keySet(), failedCompositionsInMemory.size());
        }
        return Triple.of(addedKeys.size(), fixedKeys.size(), failedCompositionsInMemory.size());
    }
}
