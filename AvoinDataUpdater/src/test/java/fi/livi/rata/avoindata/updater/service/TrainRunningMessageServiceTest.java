package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

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

        Assert.assertEquals(0, trainRunningMessageRepository.findAll().size());

        trainRunningMessageService.addTrainTreadyMessages(trainRunningMessages);

        Assert.assertEquals(2, trainRunningMessageRepository.findAll().size());
    }
}
