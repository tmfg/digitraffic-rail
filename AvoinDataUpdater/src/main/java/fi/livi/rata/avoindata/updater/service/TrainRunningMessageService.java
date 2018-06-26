package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class TrainRunningMessageService extends VersionedService<TrainRunningMessage> {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRunningMessageRepository trainReadyMessageRepository;

    public void clearTrainRunningMessages() {
        trainReadyMessageRepository.deleteAllInBatch();
    }

    public void addTrainTreadyMessages(final List<TrainRunningMessage> trainRunningMessages) {
        trainReadyMessageRepository.persist(trainRunningMessages);

        for (final TrainRunningMessage entity : trainRunningMessages) {
            if (entity.version > maxVersion.get()) {
                maxVersion.set(entity.version);
            }
        }
    }

    @Override
    public void updateObjects(final List<TrainRunningMessage> trainRunningMessages) {
        updateTrainRunningMessages(trainRunningMessages);

        for (final TrainRunningMessage entity : trainRunningMessages) {
            if (entity.version > maxVersion.get()) {
                maxVersion.set(entity.version);
            }
        }
    }

    private void updateTrainRunningMessages(final List<TrainRunningMessage> trainRunningMessages) {
        if (trainRunningMessages.isEmpty()) {
            return;
        }

        removeTrainRunningMessagesById(trainRunningMessages);
        trainReadyMessageRepository.flush();
        addTrainTreadyMessages(trainRunningMessages);
        trainReadyMessageRepository.flush();
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
