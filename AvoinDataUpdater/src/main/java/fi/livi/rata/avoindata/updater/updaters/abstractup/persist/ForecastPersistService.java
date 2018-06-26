package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import fi.livi.rata.avoindata.common.dao.train.ForecastRepository;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ForecastPersistService extends AbstractPersistService<Forecast> {
    @Autowired
    private ForecastRepository forecastRepository;

    @Override
    @Transactional
    public List<Forecast> updateEntities(final List<Forecast> entities) {

        removeEntities(entities);
        forecastRepository.flush();

        addEntities(entities);

        return entities;
    }


    public Long getMaxVersion() {
        return forecastRepository.getMaxVersion();
    }

    private void removeEntities(final List<Forecast> entities) {
        if (entities.isEmpty()) {
            return;
        }

        List<Long> idsToRemove = new ArrayList<>(entities.size());
        for (final Forecast entity : entities) {
            idsToRemove.add(entity.id);
        }
        forecastRepository.removeById(idsToRemove);
    }

    @Override
    public void clearEntities() {
        forecastRepository.deleteAllInBatch();
    }

    @Transactional
    public void addEntities(final List<Forecast> entities) {
        forecastRepository.persist(entities);
    }


}
