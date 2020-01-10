package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;

public class ElementRangeDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        final TrackWorkNotification twn = testDataService.parseEntity("ruma/rti.json",
                TrackWorkNotification.class);
        TrackWorkPart twp = twn.trackWorkParts.iterator().next();
        ArrayList<RumaLocation> locs = new ArrayList<>(twp.locations);
        locs.sort(Comparator.comparing(l -> l.locationType));
        RumaLocation loc = locs.get(1);
        IdentifierRange locir = loc.identifierRanges.iterator().next();

        assertEquals(1, locir.elementRanges.size());
        ElementRange loc2er = locir.elementRanges.iterator().next();
        assertEquals("x.x.xxx.LIVI.INFRA.14.206587", loc2er.elementId1);
        assertEquals("x.x.xxx.LIVI.INFRA.13.2785888", loc2er.elementId2);
        assertEquals(Collections.singletonList("x.x.xxx.LIVI.INFRA.44.196874"), loc2er.trackIds);
        assertEquals(Collections.singletonList("x.x.xxx.LIVI.INFRA.11.1062258"), loc2er.specifiers);
    }

}
