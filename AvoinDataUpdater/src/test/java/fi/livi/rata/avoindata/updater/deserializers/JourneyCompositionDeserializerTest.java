package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class JourneyCompositionDeserializerTest extends BaseTest {

    public static final long TEST_VERSION = 3177409547L;
    private static final Long TEST_TRAIN_NUMBER = 788L;

    @Test
    public void testDeserializer() throws Exception {
        final JourneyComposition journeyComposition = testDataService.getJourneyComposition()[0];

        assertEquals(TEST_VERSION, journeyComposition.version);
        assertEquals("vr", journeyComposition.operator.operatorShortCode);
        assertEquals(TEST_TRAIN_NUMBER, journeyComposition.trainNumber);
        assertEquals(LocalDate.of(2014, 12, 10), journeyComposition.departureDate);
        assertEquals(54L, journeyComposition.trainTypeId);
        assertEquals(184, journeyComposition.totalLength);
        assertEquals(120, journeyComposition.maximumSpeed);
//        assertEquals(new LocalTime(19, 0), journeyComposition.startStation.scheduledTime.toLocalTime());
        assertEquals("HKI", journeyComposition.startStation.stationShortCode);
        assertEquals(1, journeyComposition.startStation.stationUICCode);
        assertEquals("FI", journeyComposition.startStation.countryCode);
        assertEquals(TimeTableRow.TimeTableRowType.DEPARTURE, journeyComposition.startStation.type);
        assertEquals(7, journeyComposition.wagons.size());
        assertWagon(journeyComposition.wagons.iterator().next());
        assertEquals(1, journeyComposition.locomotives.size());
        assertLocomotive(journeyComposition.locomotives.iterator().next());
    }

    private static void assertLocomotive(Locomotive locomotive) {
        assertEquals(1, locomotive.location);
    }

    private static void assertWagon(Wagon wagon) {
        assertEquals(6, wagon.salesNumber);
        assertEquals(2, wagon.location);
        assertEquals(2590, wagon.length);
        assertNull(wagon.catering);
        assertNull(wagon.playground);
        assertNull(wagon.smoking);
        assertNull(wagon.video);
        assertTrue(wagon.pet);
        assertNull(wagon.disabled);
        assertNull(wagon.luggage);
    }
}