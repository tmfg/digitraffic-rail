package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TrackWorkNotificationDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrackWorkNotification twn = testDataService.parseEntity("ruma/rti.json",
                TrackWorkNotification.class);

        // Notification
        assertEquals(359069, twn.id.id.intValue());
        assertEquals(5, twn.id.version.intValue());
        assertEquals(TrackWorkNotificationState.ACTIVE, twn.state);
        assertEquals(true, twn.speedLimitPlan);
        assertEquals(false, twn.speedLimitRemovalPlan);
        assertEquals(false, twn.electricitySafetyPlan);
        assertEquals(true, twn.personInChargePlan);
        assertEquals(false, twn.trafficSafetyPlan);
        assertEquals("SomeOrganization", twn.organization);
        assertEquals(ZonedDateTime.parse("2018-10-09T08:30:15Z"), twn.created);
        assertEquals(ZonedDateTime.parse("2019-09-09T11:46:14Z"), twn.modified);

        // Work parts
        assertEquals(1, twn.trackWorkParts.size());
        TrackWorkPart twp = twn.trackWorkParts.iterator().next();
        assertEquals(1L, twp.partIndex.longValue());
        assertEquals(LocalDate.parse("2018-10-09"), twp.startDay);
        assertEquals(Duration.parse("PT30M"), twp.permissionMinimumDuration);
        assertEquals(LocalTime.parse("09:25"), twp.plannedWorkingGap);
        assertEquals(true, twp.containsFireWork);
        assertEquals(Collections.singletonList("x.x.xxx.LIVI.ETJ2.81.104372"), twp.advanceNotifications);

        // Locations
        ArrayList<RumaLocation> locs = new ArrayList<>(twp.locations);
        locs.sort(Comparator.comparing(l -> l.locationType));
        assertEquals(2, locs.size());

        RumaLocation loc1 = locs.get(0);
        assertEquals(LocationType.WORK, loc1.locationType);
        assertEquals("x.x.xxx.LIVI.INFRA.39.119274", loc1.operatingPointId);
        assertNull(loc1.sectionBetweenOperatingPointsId);
        assertEquals(1, loc1.identifierRanges.size());
        IdentifierRange loc1ir = loc1.identifierRanges.iterator().next();
        assertEquals("x.x.xxx.LIVI.INFRA.24.118819", loc1ir.elementId);
        assertNull(loc1ir.elementPairId1);
        assertNull(loc1ir.elementPairId2);
        assertNull(loc1ir.speedLimit);
        assertEquals(0, loc1ir.elementRanges.size());

        RumaLocation loc2 = locs.get(1);
        assertEquals(LocationType.FIREWORK, loc2.locationType);
        assertNull(loc2.operatingPointId);
        assertEquals("x.x.xxx.LIVI.INFRA.40.119859620000000", loc2.sectionBetweenOperatingPointsId);

        // Identifier ranges
        assertEquals(1, loc2.identifierRanges.size());
        IdentifierRange loc2ir = loc2.identifierRanges.iterator().next();
        assertNull(loc2ir.elementId);
        assertNull(loc2ir.elementPairId1);
        assertNull(loc2ir.elementPairId2);
        assertNull(loc2ir.speedLimit);

        // Element ranges
        assertEquals(1, loc2ir.elementRanges.size());
        ElementRange loc2er = loc2ir.elementRanges.iterator().next();
        assertEquals("x.x.xxx.LIVI.INFRA.14.206587", loc2er.elementId1);
        assertEquals("x.x.xxx.LIVI.INFRA.13.2785888", loc2er.elementId2);
        assertEquals(Collections.singletonList("x.x.xxx.LIVI.INFRA.44.196874"), loc2er.trackIds);
        assertEquals(Collections.singletonList("x.x.xxx.LIVI.INFRA.11.1062258"), loc2er.specifiers);
    }

}
