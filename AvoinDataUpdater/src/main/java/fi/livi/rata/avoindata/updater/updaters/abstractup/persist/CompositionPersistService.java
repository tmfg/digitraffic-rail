package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.composition.CompositionTimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.updater.service.CompositionService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CompositionPersistService extends AbstractPersistService<JourneyComposition> {

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private CompositionTimeTableRowRepository compositionTimeTableRowRepository;

    @Autowired
    private CompositionService compositionService;

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

        if (maxVersion != null) {
            long l = maxVersion.get();

            if (l > 0) {
                return l;
            }
        }

        return compositionRepository.getMaxVersion();
    }

    private void removeOldCompositions(final List<JourneyComposition> journeyCompositions) {
        journeyCompositions.stream().map(TrainId::new).distinct().forEach(x -> compositionRepository.deleteWithId(x.departureDate,
                x.trainNumber));
    }
}
