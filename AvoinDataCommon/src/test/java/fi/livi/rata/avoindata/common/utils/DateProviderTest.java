package fi.livi.rata.avoindata.common.utils;

import static fi.livi.rata.avoindata.common.utils.DateProvider.ZONE_ID_HKI;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;


public class DateProviderTest {

    @Test
    public void dateInHelsinki() {
        assertEquals(ZonedDateTime.now().withZoneSameInstant(ZONE_ID_HKI).toLocalDate(),
                     DateProvider.dateInHelsinki());
    }

    @Test
    public void nowInHelsinki() {
        assertEquals(
                (double) ZonedDateTime.now().withZoneSameInstant(ZONE_ID_HKI).toInstant().toEpochMilli(),
                (double) DateProvider.nowInHelsinki().toInstant().toEpochMilli(),
                1.0);
    }

}
