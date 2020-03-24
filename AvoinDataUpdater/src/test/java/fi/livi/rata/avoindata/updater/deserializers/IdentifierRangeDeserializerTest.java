package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;

import static org.junit.Assert.*;

public class IdentifierRangeDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrackWorkNotification twn = testDataService.parseEntity("ruma/rti.json",
                TrackWorkNotification.class);
        TrackWorkPart twp = twn.trackWorkParts.iterator().next();
        ArrayList<RumaLocation> locs = new ArrayList<>(twp.locations);
        locs.sort(Comparator.comparing(l -> l.locationType));
        RumaLocation loc = locs.get(1);

        assertEquals(1, loc.identifierRanges.size());
        IdentifierRange loc2ir = loc.identifierRanges.iterator().next();
        assertNull(loc2ir.elementId);
        assertNull(loc2ir.elementPairId1);
        assertNull(loc2ir.elementPairId2);
        assertNull(loc2ir.speedLimit);
        // assume that JTS deserializer works
        assertNotNull(loc2ir.locationMap);
        assertNotNull(loc2ir.locationSchema);
    }

}
