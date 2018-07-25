package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.config.MQTTConfig;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.dao.train.ForecastRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.updater.service.miku.ForecastMergingService;
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
    private MQTTPublishService mqttPublishService;

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

        return trainLockExecutor.executeInLock(() -> {
            List<Train> updatedTrains = super.doUpdate();
            try {
                mqttPublishService.publish(s -> String.format("trains/%s/%s", s.id.departureDate, s.id.trainNumber), updatedTrains, TrainJsonView.LiveTrains.class);
            } catch (Exception e) {
                log.error("Error publishing trains to MQTT", e);
            }

            return updatedTrains;
        });
    }

    @Override
    public List<Train> modifyEntitiesBeforePersist(final List<Train> entities) {
        final List<TrainId> trainIds = Lists.newArrayList(Iterables.transform(entities, f -> f.id));
        if (!trainIds.isEmpty()) {

            List<Forecast> forecasts = bes.transform(trainIds, t -> forecastRepository.findByTrains(t));

            final ImmutableListMultimap<Train, Forecast> trainMap = Multimaps.index(forecasts, forecast -> forecast.timeTableRow.train);

            for (final Train train : entities) {
                final ImmutableList<Forecast> trainsForecasts = trainMap.get(train);
                if (!trainsForecasts.isEmpty()) {
                    forecastMergingService.mergeEstimates(train, trainsForecasts);
                }
            }
        }

        return entities;
    }
}
