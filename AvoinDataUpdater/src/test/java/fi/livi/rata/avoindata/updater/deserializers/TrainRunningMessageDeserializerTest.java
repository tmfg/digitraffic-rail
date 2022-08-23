package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageTypeEnum;
import fi.livi.rata.avoindata.updater.BaseTest;

public class TrainRunningMessageDeserializerTest extends BaseTest {
    @Test
    public void trainRunningMessageShouldGetParsed() throws IOException {
        List<TrainRunningMessage> trainRunningMessages = testDataService.createTrainRunningMessages("trainRunningMessage.json");
        Assertions.assertEquals(2,trainRunningMessages.size());

        TrainRunningMessage trainRunningMessage = trainRunningMessages.get(0);
        Assertions.assertEquals(1269441928, (long)trainRunningMessage.id);
        Assertions.assertEquals(5322983063L, (long) trainRunningMessage.version);
        Assertions.assertEquals("8593", trainRunningMessage.trainId.trainNumber);
        Assertions.assertEquals(LocalDate.parse("2015-09-24"), trainRunningMessage.trainId.departureDate);
        Assertions.assertEquals(ZonedDateTime.parse("2015-09-24T21:00:56.048Z"),trainRunningMessage.timestamp);
        Assertions.assertEquals("V002", trainRunningMessage.trackSection);
        Assertions.assertEquals(null, trainRunningMessage.nextTrackSection);
        Assertions.assertEquals(null, trainRunningMessage.previousTrackSection);
        Assertions.assertEquals("EPO", trainRunningMessage.station);
        Assertions.assertEquals("KLH", trainRunningMessage.nextStation);
        Assertions.assertEquals("TRL", trainRunningMessage.previousStation);
        Assertions.assertEquals(TrainRunningMessageTypeEnum.RELEASE, trainRunningMessage.type);
    }
}
