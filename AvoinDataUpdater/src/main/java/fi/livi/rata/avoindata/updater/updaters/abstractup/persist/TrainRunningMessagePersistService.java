package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenTrainRunningMessageFilter;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class TrainRunningMessagePersistService extends AbstractPersistService<TrainRunningMessage> {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRunningMessageRepository trainReadyMessageRepository;

    @Autowired
    private RecentlySeenTrainRunningMessageFilter recentlySeenTrainRunningMessageFilter;

    @Override
    public void clearEntities() {
        trainReadyMessageRepository.deleteAllInBatch();
    }

    @Override
    public void addEntities(final List<TrainRunningMessage> entities) {
        trainReadyMessageRepository.persist(entities);

        for (final TrainRunningMessage entity : entities) {
            if (entity.version > maxVersion.get()) {
                maxVersion.set(entity.version);
            }
        }
    }

    @Override
    public List<TrainRunningMessage> updateEntities(final List<TrainRunningMessage> entities) {
        if (entities.isEmpty()) {
            return entities;
        }

        final List<TrainRunningMessage> filteredEntities = recentlySeenTrainRunningMessageFilter.filter(entities);

        if (filteredEntities.isEmpty()) {
            return entities;
        }

        removeTrainRunningMessagesById(filteredEntities);
        trainReadyMessageRepository.flush();
        addEntities(filteredEntities);
        trainReadyMessageRepository.flush();

        return filteredEntities;
    }

    private void removeTrainRunningMessagesById(final List<TrainRunningMessage> trainRunningMessages) {
        List<Long> idsToRemove = new ArrayList<>(trainRunningMessages.size());
        for (final TrainRunningMessage trainRunningMessage : trainRunningMessages) {
            idsToRemove.add(trainRunningMessage.id);
        }
        trainReadyMessageRepository.removeById(idsToRemove);
    }


    public Long getMaxVersion() {
        if (maxVersion != null) {
            long l = maxVersion.get();

            if (l > 0) {
                return l;
            }
        }

        return trainReadyMessageRepository.getMaxVersion();
    }
}