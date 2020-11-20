package fi.livi.rata.avoindata.updater.service;

import java.time.LocalDate;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.CauseFactory;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;

public class MQTTPublishServiceTest extends BaseTest {

    @Value("${updater.mqtt.server-url}")
    private String mqttServerUrl;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private CauseFactory causeFactory;

    @Before
    public void before() {
        Assume.assumeTrue(!mqttServerUrl.contains("not-a-real-url"));
    }

    @Transactional
    @Test
    public void jsonViewsAndDateTimeFormatsShouldBeHonored() throws ExecutionException, InterruptedException {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2000, 1, 1)));

        causeFactory.create(train.timeTableRows.get(0));

        Message<String> message = mqttPublishService.publishEntity("testing/testtopic", train, TrainJsonView.LiveTrains.class).get();

        DocumentContext json = JsonPath.parse(message.getPayload());

        Assert.assertEquals("testing/testtopic", message.getHeaders().get(MqttHeaders.TOPIC));
        Assert.assertEquals("2000-01-01", json.read("$['departureDate']"));
        Assert.assertEquals(24, json.<String>read("$['timetableAcceptanceDate']").length());
        Assert.assertEquals("1 koodi", json.read("$['timeTableRows'][0]['causes'][0]['categoryCode']"));
        Assert.assertEquals("3", json.read("$['timeTableRows'][0]['causes'][0]['thirdCategoryCodeId']").toString());

        assertPathNotPresent(json, "$['timeTableRows'][0]['causes'][0]['detailedCategoryName']");
    }

    @Transactional
    @Test
    public void specialCharactersShouldBeRemovedFromTopic() throws ExecutionException, InterruptedException {
        assertTopic("testing/test+topic+#/123", "testing/testtopic/123");
    }

    @Transactional
    @Test
    public void nullShouldBeEmpty() throws ExecutionException, InterruptedException {
        assertTopic("train-tracking/2018-11-12/43/RELEASE/VIA/155/null/null/null/null", "train-tracking/2018-11-12/43/RELEASE/VIA/155////");
        assertTopic("testing/null/nullify/nullable/null", "testing//nullify/nullable/");
        assertTopic("testing/null/nullify/nullable/nullable", "testing//nullify/nullable/nullable");
        assertTopic("testing/null/nullify/abcnull/abcnull", "testing//nullify/abcnull/abcnull");
        assertTopic("aws,beta/train-tracking/2018-10-26/F6418/OCCUPY/TPE/TPE_097/null/null/TPE_O097/TPE_T097", "aws,beta/train-tracking/2018-10-26/F6418/OCCUPY/TPE/TPE_097///TPE_O097/TPE_T097");
    }

    private void assertTopic(String inputTopic, String publishedTopic) throws ExecutionException, InterruptedException {
        Message<String> message = mqttPublishService.publishString(inputTopic, "content").get();

        Assert.assertEquals(publishedTopic, message.getHeaders().get(MqttHeaders.TOPIC));
    }

    @Transactional
    @Test
    public void endingNullableShouldNotBeEmpty() {

    }

    private void assertPathNotPresent(DocumentContext json, String path) {
        try {
            json.read(path);
            Assert.fail();
        } catch (PathNotFoundException e) {

        }
    }

}