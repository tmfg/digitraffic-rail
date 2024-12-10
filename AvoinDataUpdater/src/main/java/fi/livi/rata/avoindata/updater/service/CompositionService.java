package fi.livi.rata.avoindata.updater.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.composition.CompositionTimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.composition.JourneySectionRepository;
import fi.livi.rata.avoindata.common.dao.composition.LocomotiveRepository;
import fi.livi.rata.avoindata.common.dao.composition.WagonRepository;
import fi.livi.rata.avoindata.common.dao.localization.PowerTypeRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.CompositionTimeTableRow;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.utils.OptionalUtil;

@Service
@Transactional
public class CompositionService extends VersionedService<JourneyComposition> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    protected AtomicReference<Instant> maxMessageDateTime = new AtomicReference<>(Instant.MIN);

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private JourneySectionRepository journeySectionRepository;

    @Autowired
    private CompositionTimeTableRowRepository compositionTimeTableRowRepository;

    @Autowired
    private WagonRepository wagonRepository;

    @Autowired
    private LocomotiveRepository locomotiveRepository;

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @Autowired
    private PowerTypeRepository powerTypeRepository;

    @Autowired
    private TrainTypeRepository trainTypeRepository;

    public List<Composition> findCompositions() {
        return compositionRepository.findAll();
    }

    public void updateCompositions(final List<JourneyComposition> journeyCompositions) {
        removeOldCompositions(journeyCompositions);
        addCompositions(journeyCompositions);
    }

    @Override
    public void updateObjects(final List<JourneyComposition> objects) {
        updateCompositions(objects);
    }

    private void removeOldCompositions(final List<JourneyComposition> journeyCompositions) {
        journeyCompositions.stream().map(TrainId::new).distinct().forEach(
                x -> compositionRepository.deleteWithId(x.departureDate, x.trainNumber));
    }

    public void addCompositions(final List<JourneyComposition> journeyCompositions) {
        final Map<TrainId, List<JourneyComposition>> journeyCompositionsByTrain = journeyCompositions.stream().collect(
                Collectors.groupingBy(TrainId::new));
        final List<Composition> compositions = journeyCompositionsByTrain.values().stream().map(this::createCompositionFromJourneys)
                .collect(Collectors.toList());

        saveCompositions(compositions);

        maxVersion.set(compositions.stream().map(e -> e.version).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(maxVersion.get()));
        maxMessageDateTime.set(compositions.stream().map(e -> e.messageDateTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(
                maxMessageDateTime.get()));
    }

    private void saveCompositions(final List<Composition> compositions) {
        compositionRepository.persist(compositions);
        final List<JourneySection> journeySections = compositions.stream().map(x -> x.journeySections).flatMap(Collection::stream).collect(
                Collectors.toList());
        final List<CompositionTimeTableRow> compositionTimeTableRows = Stream.concat(journeySections.stream().map(x -> x.beginTimeTableRow),
                journeySections.stream().map(x -> x.endTimeTableRow)).collect(Collectors.toList());
        compositionTimeTableRowRepository.persist(compositionTimeTableRows);
        journeySectionRepository.persist(journeySections);
        locomotiveRepository.persist(journeySections.stream().map(x -> x.locomotives).flatMap(Collection::stream)
                .collect(Collectors.toList()));
        wagonRepository.persist(journeySections.stream().map(x -> x.wagons).flatMap(Collection::stream).collect(Collectors.toList()));
    }

    private Composition createCompositionFromJourneys(final List<JourneyComposition> journeyCompositions) {
        final Composition composition = createCompositionFromJourneyCompositions(journeyCompositions);
        composition.journeySections = createSortedJourneySectionsFromJourneyCompositions(journeyCompositions, composition);
        return composition;
    }


    private Composition createCompositionFromJourneyCompositions(final List<JourneyComposition> journeyCompositions) {
        final JourneyComposition journeyComposition = journeyCompositions.getFirst();

        final long maxVersion = journeyCompositions.stream().map(c -> c.version).max(Comparator.naturalOrder()).orElse(Long.MIN_VALUE);
        final Instant maxMessageReference = journeyCompositions.stream().map(c -> c.messageDateTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

        final Composition composition = new Composition(journeyComposition.operator, journeyComposition.trainNumber,
                journeyComposition.departureDate, journeyComposition.trainCategoryId, journeyComposition.trainTypeId, maxVersion, maxMessageReference);

        final Optional<TrainCategory> trainCategoryOptional = trainCategoryRepository.findByIdCached(journeyComposition.trainCategoryId);
        final Optional<TrainType> trainTypeOptional = trainTypeRepository.findByIdCached(journeyComposition.trainTypeId);
        composition.trainCategory = trainCategoryOptional.isPresent() ? trainCategoryOptional.get().name : "";
        composition.trainType = trainTypeOptional.isPresent() ? trainTypeOptional.get().name : "";

        return composition;
    }

    private LinkedHashSet<JourneySection> createSortedJourneySectionsFromJourneyCompositions(
            final List<JourneyComposition> journeyCompositions, final Composition composition) {
        return journeyCompositions.stream().map(x -> createJourneySection(x, composition))
                .sorted(Comparator.comparing(o -> o.beginTimeTableRow.scheduledTime)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private JourneySection createJourneySection(final JourneyComposition journeyComposition, final Composition composition) {
        final CompositionTimeTableRow beginTimeTableRow = new CompositionTimeTableRow(journeyComposition.startStation);

        final CompositionTimeTableRow endTimeTableRow =
            journeyComposition.endStation != null ?
                new CompositionTimeTableRow(journeyComposition.endStation) : null;
        if ( endTimeTableRow == null) {
            log.error("method=createJourneySection JourneyComposition for train {} on {} had empty endStation", journeyComposition.trainNumber, journeyComposition.departureDate);
        }

        final JourneySection journeySection = new JourneySection(beginTimeTableRow, endTimeTableRow, composition,
                journeyComposition.maximumSpeed, journeyComposition.totalLength, journeyComposition.attapId, journeyComposition.saapAttapId);
        journeySection.locomotives = journeyComposition.locomotives.stream().map(x -> new Locomotive(x, journeySection))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        journeyComposition.journeySection = journeySection;
        journeyComposition.wagons.forEach(x -> x.journeysection = journeySection);
        journeySection.wagons = new LinkedHashSet<>(journeyComposition.wagons);

        for (final Locomotive locomotive : journeySection.locomotives) {
            locomotive.powerType = OptionalUtil.getName(powerTypeRepository.findByAbbreviationCached(locomotive.powerTypeAbbreviation));
        }

        return journeySection;
    }

    public void clearCompositions() {
        compositionRepository.deleteAllInBatch();
        compositionTimeTableRowRepository.deleteAllInBatch();
    }

    public Long getMaxVersion() {
        if (maxVersion.get() <= 0) {
            maxVersion.set(compositionRepository.getMaxVersion());
        }
        return maxVersion.get();
    }

    /**
     * @return messageDateTime of julkisetkokoonpanot message in EpochMilli
     */
    public Instant getMaxMessageDateTime() {
        if (!maxMessageDateTime.get().isAfter(Instant.MIN)) {
            maxMessageDateTime.set(ObjectUtils.firstNonNull(compositionRepository.getMaxMessageDateTime(), Instant.MIN));

            final Instant weekInPast = Instant.now().minus(7, ChronoUnit.DAYS);
            if (maxMessageDateTime.get().isBefore(weekInPast)) { // Initially get max last 7 days
                maxMessageDateTime.set(weekInPast);
                log.info("method=getMaxMessageDateTime initial value now-7d: {}", maxMessageDateTime.get());
            } else {
                log.info("method=getMaxMessageDateTime initial value from db {}", maxMessageDateTime.get());
            }
        }
        return maxMessageDateTime.get();
    }
}
