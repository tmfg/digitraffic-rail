package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;


import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.TrainPublishingService;
import fi.livi.rata.avoindata.updater.service.routeset.TimeTableRowByRoutesetUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

@Service
public class TrainInitializerService extends AbstractDatabaseInitializer<Train> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainPersistService trainPersistService;

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private TrainPublishingService trainPublishingService;

    @Autowired
    private TimeTableRowByRoutesetUpdateService timeTableRowByRoutesetUpdateService;

    @Override
    public String getPrefix() {
        return "trains";
    }

    @Override
    public AbstractPersistService<Train> getPersistService() {
        return trainPersistService;
    }

    @Override
    protected Class<Train[]> getEntityCollectionClass() {
        return Train[].class;
    }

    @Override
    protected List<Train> doUpdate() {
        final var updatedTrains = trainLockExecutor.executeInLock(this.getPrefix(), super::doUpdate);

        trainPublishingService.publish(updatedTrains);

        return updatedTrains;
    }

    @Override
    public List<Train> modifyEntitiesBeforePersist(final List<Train> entities) {
        final List<TrainId> trainIds = Lists.newArrayList(Iterables.transform(entities, f -> f.id));
        if (!trainIds.isEmpty()) {
            mergeRoutesets(entities);
        }

        return entities;
    }

    private void mergeRoutesets(final List<Train> entities) {
        timeTableRowByRoutesetUpdateService.updateByTrains(entities);
    }
}
