package fi.livi.rata.avoindata.updater.deserializers;

import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class TrackWorkNotificationDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrackWorkNotification twn = testDataService.parseEntity("ruma/rti.json",
                TrackWorkNotification.class);

        assertEquals(359069L, twn.id.id.longValue());
        assertEquals(5L, twn.id.version.longValue());
        assertEquals(TrackWorkNotificationState.ACTIVE, twn.state);
        assertEquals(true, twn.speedLimitPlan);
        assertEquals(false, twn.speedLimitRemovalPlan);
        assertEquals(false, twn.electricitySafetyPlan);
        assertEquals(true, twn.personInChargePlan);
        assertEquals(false, twn.trafficSafetyPlan);
        assertEquals("SomeOrganization", twn.organization);
        assertEquals(ZonedDateTime.parse("2018-10-09T08:30:15Z"), twn.created);
        assertEquals(ZonedDateTime.parse("2019-09-09T11:46:14Z"), twn.modified);
        assertEquals(539718, ((Double)((Point) twn.locationMap).getX()).intValue());
        assertEquals(7141962, ((Double)((Point) twn.locationMap).getY()).intValue());
        assertEquals(539666, ((Double)((Point) twn.locationSchema).getX()).intValue());
        assertEquals(7142004, ((Double)((Point) twn.locationSchema).getY()).intValue());
    }

}
