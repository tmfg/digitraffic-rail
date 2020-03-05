package fi.livi.rata.avoindata.updater.deserializers;

import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class TrafficRestrictionNotificationDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrafficRestrictionNotification trn = testDataService.parseEntity("ruma/lri.json",
                TrafficRestrictionNotification.class);

        assertEquals(837023, trn.id.id.longValue());
        assertEquals(2L, trn.id.version.longValue());
        assertEquals(TrafficRestrictionNotificationState.SENT, trn.state);
        assertEquals("Org Oy", trn.organization);
        assertEquals(ZonedDateTime.parse("2020-02-19T07:20:18Z"), trn.created);
        assertEquals(ZonedDateTime.parse("2020-02-19T07:25:22Z"), trn.modified);
        assertEquals(328298, ((Double)((Point) trn.locationMap).getX()).intValue());
        assertEquals(6822565, ((Double)((Point) trn.locationMap).getY()).intValue());
        assertEquals(328359, ((Double)((Point) trn.locationSchema).getX()).intValue());
        assertEquals(ZonedDateTime.parse("2020-02-19T07:25:22Z"), trn.finished);
        assertEquals("123", trn.twnId);
        assertEquals(ZonedDateTime.parse("2020-02-19T07:00:00Z"), trn.startDate);
        assertEquals(ZonedDateTime.parse("2020-02-19T10:00:00Z"), trn.endDate);
        assertEquals(TrafficRestrictionType.TEMPORARY_SPEED_LIMIT, trn.limitation);
    }

}
