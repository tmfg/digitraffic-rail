package fi.livi.rata.avoindata.updater.service;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.BaseTest;

public class TrainRunningMessageServiceTest extends BaseTest {
    @Autowired
    private TrainRunningMessageService trainRunningMessageService;

    @Autowired
    private TrainRunningMessageRepository trainRunningMessageRepository;

    @Test
    @Transactional
    public void addShouldNotProduceErrors() throws IOException {
        List<TrainRunningMessage> trainRunningMessages = testDataService.createTrainRunningMessages("trainRunningMessage.json");

        trainRunningMessageService.clearTrainRunningMessages();

        Assertions.assertEquals(0, trainRunningMessageRepository.findAll().size());

        trainRunningMessageService.addTrainTreadyMessages(trainRunningMessages);

        Assertions.assertEquals(2, trainRunningMessageRepository.findAll().size());
    }
}
