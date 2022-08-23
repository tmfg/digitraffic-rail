package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TrainDeserializerTest extends BaseTest {
    @Test
    public void testDeserializer() throws Exception {
        final List<Train> trains = testDataService.parseTrains("trainsSingle.json");

        assertEquals(1, trains.size());
        final Train train = trains.get(0);
        assertEquals(new Long(10600L), train.id.trainNumber);
        assertEquals(LocalDate.of(2014, 12, 10), train.id.departureDate);
        assertEquals(10, train.operator.operatorUICCode);
        assertEquals("vr", train.operator.operatorShortCode);
        assertEquals(52L, train.trainTypeId);
        assertEquals("V", train.commuterLineID);
        assertFalse(train.runningCurrently);
        assertFalse(train.cancelled);
        assertEquals(46, train.timeTableRows.size());
        assertEquals(2, train.timeTableRows.stream().filter(x -> x.commercialStop != null).count());
        assertEquals(44, train.timeTableRows.stream().filter(x -> x.commercialStop == null).count());
    }

    @Test
    public void oldTrainShouldNotBeRunningCurrently() throws Exception {
        final List<Train> trains = testDataService.parseTrains("trainsSingleRunningCurrently.json");

        assertEquals(1, trains.size());
        final Train train = trains.get(0);

        assertFalse(train.runningCurrently);
    }
}