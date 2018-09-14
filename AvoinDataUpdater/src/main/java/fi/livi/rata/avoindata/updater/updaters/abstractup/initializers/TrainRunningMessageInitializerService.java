package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainRunningMessagePersistService;

@Service
public class TrainRunningMessageInitializerService extends AbstractDatabaseInitializer<TrainRunningMessage> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRunningMessagePersistService trainRunningMessagePersistService;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Override
    public String getPrefix() {
        return "trainrunningmessages";
    }

    @Override
    public AbstractPersistService<TrainRunningMessage> getPersistService() {
        return trainRunningMessagePersistService;
    }

    @Override
    protected Class<TrainRunningMessage[]> getEntityCollectionClass() {
        return TrainRunningMessage[].class;
    }

    @Override
    protected List<TrainRunningMessage> doUpdate() {
        List<TrainRunningMessage> updatedTrainRunningMessages = super.doUpdate();

        try {
            mqttPublishService.publish(s -> String.format("train-tracking/%s/%s", s.trainId.departureDate, s.trainId.trainNumber),
                    updatedTrainRunningMessages);
        } catch (Exception e) {
            log.error("Error publishing trains to MQTT", e);
        }

        return updatedTrainRunningMessages;
    }
}
