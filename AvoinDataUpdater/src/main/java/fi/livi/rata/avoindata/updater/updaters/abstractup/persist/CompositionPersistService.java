package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import java.time.Instant;
import java.util.List;

import fi.livi.rata.avoindata.common.dao.stopsector.StopSectorQueueItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.composition.CompositionTimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.updater.service.CompositionService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;

@Service
@Transactional
public class CompositionPersistService extends AbstractPersistService<JourneyComposition> {
    private final CompositionRepository compositionRepository;
    private final CompositionTimeTableRowRepository compositionTimeTableRowRepository;

    private final CompositionService compositionService;

    public CompositionPersistService(final CompositionRepository compositionRepository, final CompositionTimeTableRowRepository compositionTimeTableRowRepository, final CompositionService compositionService) {
        this.compositionRepository = compositionRepository;
        this.compositionTimeTableRowRepository = compositionTimeTableRowRepository;
        this.compositionService = compositionService;
    }

    @Override
    public List<JourneyComposition> updateEntities(final List<JourneyComposition> entities) {
        removeOldCompositions(entities);
        addEntities(entities);
        return entities;
    }

    @Override
    public void addEntities(final List<JourneyComposition> entities) {
        compositionService.addCompositions(entities);
    }

    @Override
    public void clearEntities() {
        compositionRepository.deleteAllInBatch();
        compositionTimeTableRowRepository.deleteAllInBatch();
    }

    public Long getMaxVersion() {
        return compositionService.getMaxVersion();
    }

    /**
     * @return messageDateTime of julkisetkokoonpanot message in EpochMilli
     */
    public Instant getMaxMessageDateTime() {
        return compositionService.getMaxMessageDateTime();
    }

    private void removeOldCompositions(final List<JourneyComposition> journeyCompositions) {
        journeyCompositions.stream().map(TrainId::new).distinct().forEach(x -> compositionRepository.deleteWithId(x.departureDate,
                x.trainNumber));
    }
}
