package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class TrackWorkPartDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrackWorkNotification twn = testDataService.parseEntity("ruma/rti.json",
                TrackWorkNotification.class);

        assertEquals(1, twn.trackWorkParts.size());
        TrackWorkPart twp = twn.trackWorkParts.iterator().next();
        assertEquals(1L, twp.partIndex.longValue());
        assertEquals(LocalDate.parse("2018-10-09"), twp.startDay);
        assertEquals(Duration.parse("PT30M"), twp.permissionMinimumDuration);
        assertEquals(LocalTime.parse("09:25"), twp.plannedWorkingGap);
        assertEquals(true, twp.containsFireWork);
        assertEquals(Collections.singletonList("1.2.246.586.2.81.104372"), twp.advanceNotifications);
    }

}
