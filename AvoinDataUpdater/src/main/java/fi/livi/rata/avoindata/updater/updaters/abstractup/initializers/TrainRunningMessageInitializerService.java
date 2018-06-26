package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainRunningMessagePersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrainRunningMessageInitializerService extends AbstractDatabaseInitializer<TrainRunningMessage> {
    @Autowired
    private TrainRunningMessagePersistService trainRunningMessagePersistService;

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
}
