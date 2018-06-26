package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageTypeEnum;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public class TrainRunningMessageDeserializerTest extends BaseTest {
    @Test
    public void trainRunningMessageShouldGetParsed() throws IOException {
        List<TrainRunningMessage> trainRunningMessages = testDataService.createTrainRunningMessages("trainRunningMessage.json");
        Assert.assertEquals(2,trainRunningMessages.size());

        TrainRunningMessage trainRunningMessage = trainRunningMessages.get(0);
        Assert.assertEquals(1269441928, (long)trainRunningMessage.id);
        Assert.assertEquals(5322983063L, (long) trainRunningMessage.version);
        Assert.assertEquals("8593", trainRunningMessage.trainId.trainNumber);
        Assert.assertEquals(LocalDate.parse("2015-09-24"), trainRunningMessage.trainId.departureDate);
        Assert.assertEquals(ZonedDateTime.parse("2015-09-24T21:00:56.048Z"),trainRunningMessage.timestamp);
        Assert.assertEquals("V002", trainRunningMessage.trackSection);
        Assert.assertEquals(null, trainRunningMessage.nextTrackSection);
        Assert.assertEquals(null, trainRunningMessage.previousTrackSection);
        Assert.assertEquals("EPO", trainRunningMessage.station);
        Assert.assertEquals("KLH", trainRunningMessage.nextStation);
        Assert.assertEquals("TRL", trainRunningMessage.previousStation);
        Assert.assertEquals(TrainRunningMessageTypeEnum.RELEASE, trainRunningMessage.type);
    }
}
