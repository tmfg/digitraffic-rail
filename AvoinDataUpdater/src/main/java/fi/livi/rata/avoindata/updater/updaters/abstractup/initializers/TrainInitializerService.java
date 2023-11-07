package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;


import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import fi.livi.rata.avoindata.common.dao.train.ForecastRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.TrainPublishingService;
import fi.livi.rata.avoindata.updater.service.miku.ForecastMergingService;
import fi.livi.rata.avoindata.updater.service.routeset.TimeTableRowByRoutesetUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

@Service
public class TrainInitializerService extends AbstractDatabaseInitializer<Train> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainPersistService trainPersistService;

    @Autowired
    private ForecastMergingService forecastMergingService;

    @Autowired
    private ForecastRepository forecastRepository;

    @Autowired
    private BatchExecutionService bes;

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
        return trainLockExecutor.executeInLock(this.getPrefix(), () -> {
            final List<Train> updatedTrains = super.doUpdate();

            trainPublishingService.publish(updatedTrains);

            return updatedTrains;
        });
    }



    @Override
    public List<Train> modifyEntitiesBeforePersist(final List<Train> entities) {
        final List<TrainId> trainIds = Lists.newArrayList(Iterables.transform(entities, f -> f.id));
        if (!trainIds.isEmpty()) {
            mergeForecasts(entities, trainIds);
            mergeRoutesets(entities);
        }

        return entities;
    }

    private void mergeRoutesets(final List<Train> entities) {
        timeTableRowByRoutesetUpdateService.updateByTrains(entities);
    }

    private void mergeForecasts(List<Train> entities, List<TrainId> trainIds) {
        List<Forecast> forecasts = bes.transform(trainIds
            .stream().collect(Collectors.toSet())
            .stream().sorted()
            .collect(Collectors.toList()), t -> forecastRepository.findByTrains(t), 100);

        final ImmutableListMultimap<Train, Forecast> trainMap = Multimaps.index(forecasts, forecast -> forecast.timeTableRow.train);

        for (final Train train : entities) {
            final ImmutableList<Forecast> trainsForecasts = trainMap.get(train);
            if (!trainsForecasts.isEmpty()) {
                forecastMergingService.mergeEstimates(train, trainsForecasts);
            }
        }
    }
}
