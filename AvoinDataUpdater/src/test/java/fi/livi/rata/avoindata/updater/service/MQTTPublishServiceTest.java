package fi.livi.rata.avoindata.updater.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.CauseFactory;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public class MQTTPublishServiceTest extends BaseTest {
    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private CauseFactory causeFactory;

    @Transactional
    @Test
    public void datetimeShouldBeInCorrectFormat() {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2000, 1, 1)));

        causeFactory.create(train.timeTableRows.get(0));

        Message<String> message = mqttPublishService.publishEntity("testing/testtopic", train, TrainJsonView.LiveTrains.class);

        DocumentContext json = JsonPath.parse(message.getPayload());

        Assert.assertEquals("testing/testtopic", message.getHeaders().get(MqttHeaders.TOPIC));
        Assert.assertEquals("2000-01-01", json.read("$['departureDate']"));
        Assert.assertEquals(24, json.<String>read("$['timetableAcceptanceDate']").length());
        Assert.assertEquals("1 koodi", json.read("$['timeTableRows'][0]['causes'][0]['categoryCode']"));
        Assert.assertEquals("3", json.read("$['timeTableRows'][0]['causes'][0]['thirdCategoryCodeId']").toString());

        try {
            json.read("$['timeTableRows'][0]['causes'][0]['detailedCategoryName']");
            Assert.fail();
        } catch (PathNotFoundException e) {

        }
    }

}