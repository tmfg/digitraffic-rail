package fi.livi.rata.avoindata.updater.deserializers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;

public class TrainLocationDeserializerTest extends BaseTest {

    @Test
    public void testDeserializer() throws Exception {
        final List<TrainLocation> trainLocations = testDataService.parseEntityList("trainlocation/train-location.json",
                TrainLocation[].class);

        Assertions.assertEquals(1, trainLocations.size());
        final TrainLocation trainLocation = trainLocations.get(0);

        Assertions.assertEquals(9931L, trainLocation.trainLocationId.trainNumber.longValue());
        Assertions.assertEquals(LocalDate.of(2017, 11, 6), trainLocation.trainLocationId.departureDate);
        Assertions.assertEquals(ZonedDateTime.of(2017, 11, 06, 8, 17, 50, 0, ZoneId.of("UTC")),
                trainLocation.trainLocationId.timestamp.withZoneSameInstant(ZoneId.of("UTC")));

        Assertions.assertEquals(74, trainLocation.speed.intValue());

        Assertions.assertEquals(24.799053, trainLocation.location.getX(), 0.00001);
        Assertions.assertEquals(60.742345, trainLocation.location.getY(), 0.00001);
    }
}
