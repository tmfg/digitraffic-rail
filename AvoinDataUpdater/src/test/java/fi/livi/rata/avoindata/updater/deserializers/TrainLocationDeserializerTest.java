package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationConnectionQuality;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class TrainLocationDeserializerTest extends BaseTest {

    @Test
    public void testDeserializer() throws Exception {
        final List<TrainLocation> trainLocations = testDataService.parseEntityList("trainlocation/train-location.json",
                TrainLocation[].class);

        Assert.assertEquals(1, trainLocations.size());
        final TrainLocation trainLocation = trainLocations.get(0);

        Assert.assertEquals(9931L, trainLocation.trainLocationId.trainNumber.longValue());
        Assert.assertEquals(LocalDate.of(2017, 11, 6), trainLocation.trainLocationId.departureDate);
        Assert.assertEquals(ZonedDateTime.of(2017, 11, 06, 8, 17, 50, 0, ZoneId.of("UTC")),
                trainLocation.trainLocationId.timestamp.withZoneSameInstant(ZoneId.of("UTC")));

        Assert.assertEquals(74, trainLocation.speed.intValue());
        Assert.assertEquals(TrainLocationConnectionQuality.OK, trainLocation.connectionQuality);

        Assert.assertEquals(24.799053, trainLocation.location.getX(), 0.00001);
        Assert.assertEquals(60.742345, trainLocation.location.getY(), 0.00001);
    }
}