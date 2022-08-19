package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.trackwork.ElementRange;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ElementRangeDeserializerTest extends BaseTest {

    @Test
    public void deserialize() throws Exception {
        ElementRange er = testDataService.parseEntity("ruma/elementrange.json",
                ElementRange.class);
        assertEquals("1.2.246.586.1.14.206587", er.elementId1);
        assertEquals("1.2.246.586.1.13.2785888", er.elementId2);
        assertEquals(Collections.singletonList("1.2.246.586.1.44.196874"), er.trackIds);
        assertEquals(Collections.singletonList("1.2.246.586.1.11.1062258"), er.specifiers);
        assertEquals("(001) 53+0403 > 343+0082", er.trackKilometerRange);
    }

}
