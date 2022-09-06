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

        Assertions.assertEquals(2, trainLocations.size());
        final TrainLocation tl1 = trainLocations.get(0);
        final TrainLocation tl2 = trainLocations.get(1);

        Assertions.assertEquals(9931L, tl1.trainLocationId.trainNumber.longValue());
        Assertions.assertEquals(LocalDate.of(2017, 11, 6), tl1.trainLocationId.departureDate);
        Assertions.assertEquals(ZonedDateTime.of(2017, 11, 06, 8, 17, 50, 0, ZoneId.of("UTC")),
                tl1.trainLocationId.timestamp.withZoneSameInstant(ZoneId.of("UTC")));

        Assertions.assertEquals(74, tl1.speed.intValue());

        Assertions.assertEquals(24.799053, tl1.location.getX(), 0.00001);
        Assertions.assertEquals(60.742345, tl1.location.getY(), 0.00001);

        // first one has accuracy
        Assertions.assertEquals(11, tl1.accuracy);
        // second one does not
        Assertions.assertNull(tl2.accuracy);
    }
}
