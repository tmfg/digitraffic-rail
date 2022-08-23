package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class RumaLocationDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrackWorkNotification twn = testDataService.parseEntity("ruma/rti.json",
                TrackWorkNotification.class);
        TrackWorkPart twp = twn.trackWorkParts.iterator().next();

        // Locations
        ArrayList<RumaLocation> locs = new ArrayList<>(twp.locations);
        locs.sort(Comparator.comparing(l -> l.locationType));
        assertEquals(2, locs.size());

        RumaLocation loc1 = locs.get(0);
        assertEquals(LocationType.WORK, loc1.locationType);
        assertEquals("1.2.246.586.1.39.119274", loc1.operatingPointId);
        assertNull(loc1.sectionBetweenOperatingPointsId);
        assertEquals(1, loc1.identifierRanges.size());
        IdentifierRange loc1ir = loc1.identifierRanges.iterator().next();
        assertEquals("1.2.246.586.1.24.118819", loc1ir.elementId);
        assertNull(loc1ir.elementPairId1);
        assertNull(loc1ir.elementPairId2);
        assertNull(loc1ir.speedLimit);
        assertEquals(0, loc1ir.elementRanges.size());
        // assume that JTS deserializer works
        assertNotNull(loc1ir.locationMap);
        assertNotNull(loc1ir.locationSchema);

        RumaLocation loc2 = locs.get(1);
        assertEquals(LocationType.FIREWORK, loc2.locationType);
        assertNull(loc2.operatingPointId);
        assertEquals("1.2.246.586.1.40.119859620000000", loc2.sectionBetweenOperatingPointsId);
    }

}
