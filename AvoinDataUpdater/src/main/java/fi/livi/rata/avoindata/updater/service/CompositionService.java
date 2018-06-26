package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.composition.*;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class CompositionService extends VersionedService<JourneyComposition> {

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
        journeyCompositions.stream().map(TrainId::new).distinct().forEach(x -> compositionRepository.deleteWithId(x.departureDate,
                x.trainNumber));
    }

    public void addCompositions(final List<JourneyComposition> journeyCompositions) {
        final Map<TrainId, List<JourneyComposition>> journeyCompositionsByTrain = journeyCompositions.stream().collect(
                Collectors.groupingBy(TrainId::new));
        final List<Composition> compositions = journeyCompositionsByTrain.values().stream().map(
                CompositionService::createCompositionFromJourneys).collect(Collectors.toList());

        saveCompositions(compositions);

        for (final Composition entity : compositions) {
            if (entity.version > maxVersion.get()) {
                maxVersion.set(entity.version);
            }
        }
    }

    private void saveCompositions(final List<Composition> compositions) {
        compositionRepository.persist(compositions);
        final List<JourneySection> journeySections = compositions.stream().map(x -> x.journeySections).flatMap(Collection::stream).collect(
                Collectors.toList());
        final List<CompositionTimeTableRow> compositionTimeTableRows = Stream.concat(journeySections.stream().map(x -> x.beginTimeTableRow),
                journeySections.stream().map(x -> x.endTimeTableRow)).collect(Collectors.toList());
        compositionTimeTableRowRepository.persist(compositionTimeTableRows);
        journeySectionRepository.persist(journeySections);
        locomotiveRepository.persist(journeySections.stream().map(x -> x.locomotives).flatMap(Collection::stream).collect(
                Collectors.toList()));
        wagonRepository.persist(journeySections.stream().map(x -> x.wagons).flatMap(Collection::stream).collect(Collectors.toList()));
    }

    public static Composition createCompositionFromJourneys(final List<JourneyComposition> journeyCompositions) {
        final Composition composition = createCompositionFromJourneyCompositions(journeyCompositions);
        composition.journeySections = createSortedJourneySectionsFromJourneyCompositions(journeyCompositions, composition);
        return composition;
    }


    private static Composition createCompositionFromJourneyCompositions(final List<JourneyComposition> journeyCompositions) {
        final JourneyComposition journeyComposition = journeyCompositions.get(0);

        Long maxVersion = Long.MIN_VALUE;
        for (final JourneyComposition composition : journeyCompositions) {
            if (composition.version > maxVersion) {
                maxVersion = composition.version;
            }
        }

        return new Composition(journeyComposition.operator, journeyComposition.trainNumber, journeyComposition.departureDate,
                journeyComposition.trainCategoryId, journeyComposition.trainTypeId, maxVersion);
    }

    private static LinkedHashSet<JourneySection> createSortedJourneySectionsFromJourneyCompositions(
            final List<JourneyComposition> journeyCompositions, final Composition composition) {
        return new LinkedHashSet<>(journeyCompositions.stream().map(x -> createJourneySection(x, composition)).sorted(
                Comparator.comparing(o -> o.beginTimeTableRow.scheduledTime)).collect(Collectors.toList()));
    }

    private static JourneySection createJourneySection(final JourneyComposition journeyComposition, final Composition composition) {
        final CompositionTimeTableRow beginTimeTableRow = new CompositionTimeTableRow(journeyComposition.startStation, composition);

        CompositionTimeTableRow endTimeTableRow = null;
        if (journeyComposition.endStation != null) {
            endTimeTableRow = new CompositionTimeTableRow(journeyComposition.endStation, composition);
        }

        final JourneySection journeySection = new JourneySection(beginTimeTableRow, endTimeTableRow, composition, journeyComposition
                .maximumSpeed, journeyComposition.totalLength);
        journeySection.locomotives = new LinkedHashSet<>(journeyComposition.locomotives.stream().map(x -> new Locomotive(x, journeySection))
                .collect(Collectors.toList()));

        journeyComposition.wagons.forEach(x -> x.journeysection = journeySection);
        journeySection.wagons = new LinkedHashSet<>(journeyComposition.wagons);

        return journeySection;
    }

    public void clearCompositions() {
        compositionRepository.deleteAllInBatch();
        compositionTimeTableRowRepository.deleteAllInBatch();
    }

    public Long getMaxVersion() {

        if (maxVersion != null) {
            long l = maxVersion.get();

            if (l > 0) {
                return l;
            }
        }

        return compositionRepository.getMaxVersion();
    }
}
