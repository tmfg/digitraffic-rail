package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRuleRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class TrainRunningMessageRuleService {
    @Autowired
    private TrainRunningMessageRuleRepository trainRunningMessageRuleRepository;


    @Transactional
    public void update(final TrainRunningMessageRule[] data) {
        trainRunningMessageRuleRepository.deleteAllInBatch();
        trainRunningMessageRuleRepository.persist(Arrays.asList(data));
    }
}
