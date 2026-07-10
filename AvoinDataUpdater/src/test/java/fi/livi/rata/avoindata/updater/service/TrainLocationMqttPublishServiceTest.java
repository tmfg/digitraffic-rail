package fi.livi.rata.avoindata.updater.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.test.context.TestPropertySource;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.BaseTest;

/**
 * Contract tests for the TrainLocation MQTT payload. The entity is built inline (as production does via the
 * deserializer, not a factory) and serialized by the updater's MQTT ObjectMapper.
 *
 * <p>mqtt.enable=false disables actual broker delivery, so these serialization contract tests run without a live
 * broker; {@code publishEntity(...)} still builds and returns the message.
 */
@TestPropertySource(properties = { "mqtt.enable=false" })
public class TrainLocationMqttPublishServiceTest extends BaseTest {

    // The updater/MQTT ObjectMapper serializes ZonedDateTime with the Jackson default (UTC 'Z', variable
    // sub-second precision — observed as microseconds, e.g. 2026-07-07T13:06:18.501444Z). This does NOT match the REST
    // API's fixed millisecond format yyyy-MM-dd'T'HH:mm:ss.SSS'Z'. MQTT and REST timestamp precision differ.
    private static final String MQTT_UTC_TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    @Autowired
    private MQTTPublishService mqttPublishService;

    private static TrainLocation trainLocation() {
        final GeometryFactory gf = new GeometryFactory();
        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.trainLocationId = new TrainLocationId(1L, LocalDate.of(2026, 1, 1), ZonedDateTime.now());
        trainLocation.location = gf.createPoint(new Coordinate(24.5, 61.5)); // WGS84 lon/lat
        trainLocation.speed = 100;
        trainLocation.accuracy = 11;
        return trainLocation;
    }

    @Test
    public void trainLocationPayloadShouldBeCorrect() throws ExecutionException, InterruptedException {
        final Message<String> message = mqttPublishService.publishEntity("train-locations/2026-01-01/1", trainLocation(), null).get();
        final DocumentContext json = JsonPath.parse(message.getPayload());

        Assertions.assertEquals(1, ((Number) json.read("$['trainNumber']")).intValue());
        Assertions.assertEquals("2026-01-01", json.read("$['departureDate']"));
        Assertions.assertEquals(100, ((Number) json.read("$['speed']")).intValue());
        Assertions.assertEquals(11, ((Number) json.read("$['accuracy']")).intValue());

        final String timestamp = json.read("$['timestamp']");
        Assertions.assertTrue(timestamp.matches(MQTT_UTC_TIMESTAMP_REGEX),
                "MQTT timestamp should be ISO-8601 UTC (see FINDING above), was: " + timestamp);

        // location must serialize as a GeoJSON Point (JtsModule) — same shape as REST V1
        Assertions.assertEquals("Point", json.read("$['location']['type']"));
        Assertions.assertEquals(24.5, ((Number) json.read("$['location']['coordinates'][0]")).doubleValue(), 0.0001);
        Assertions.assertEquals(61.5, ((Number) json.read("$['location']['coordinates'][1]")).doubleValue(), 0.0001);

        // internal fields must not leak
        assertPathNotPresent(json, "$['id']");
        assertPathNotPresent(json, "$['liikeLocation']");
    }

    @Test
    public void trainLocationTopicShouldBeCorrect() throws ExecutionException, InterruptedException {
        final TrainLocation trainLocation = trainLocation();

        // Pin the production topic format built in TrainLocationUpdater: train-locations/{departureDate}/{trainNumber}
        final String topic = String.format("train-locations/%s/%s", trainLocation.trainLocationId.departureDate,
                trainLocation.trainLocationId.trainNumber);
        final Message<String> message = mqttPublishService.publishEntity(topic, trainLocation, null).get();

        Assertions.assertEquals("train-locations/2026-01-01/1", message.getHeaders().get(MqttHeaders.TOPIC));
    }

    // --- Test 17: isGpsLocation in MQTT payload ---

    @Test
    public void mqttPayloadShouldContainIsGpsLocation() throws ExecutionException, InterruptedException {
        // given — entity with isGpsLocation = true (default)
        final TrainLocation tl = trainLocation();

        // when
        final Message<String> message = mqttPublishService.publishEntity("train-locations/2026-01-01/1", tl, null).get();
        final DocumentContext json = JsonPath.parse(message.getPayload());

        // then — isGpsLocation should be present as boolean
        Assertions.assertTrue((Boolean) json.read("$['isGpsLocation']"),
                "MQTT payload should contain isGpsLocation=true");
    }

    @Test
    public void mqttPayloadShouldContainIsGpsLocationFalse() throws ExecutionException, InterruptedException {
        // given — entity with isGpsLocation = false
        final TrainLocation tl = trainLocation();
        tl.isGpsLocation = false;

        // when
        final Message<String> message = mqttPublishService.publishEntity("train-locations/2026-01-01/1", tl, null).get();
        final DocumentContext json = JsonPath.parse(message.getPayload());

        // then — key name must match REST convention (isGpsLocation, not is_gps_location)
        Assertions.assertFalse((Boolean) json.read("$['isGpsLocation']"),
                "MQTT payload should contain isGpsLocation=false");
    }

    private static void assertPathNotPresent(final DocumentContext json, final String path) {
        try {
            json.read(path);
            Assertions.fail("Expected path to be absent: " + path);
        } catch (final PathNotFoundException e) {
            // expected
        }
    }
}
