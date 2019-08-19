package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class StationControllerTest extends MockMvcBaseTest {
    @Test
    @Transactional
    public void geoJsonWorks() throws Exception {
        getGeoJson("/metadata/stations.geojson").andExpect(jsonPath("$.features").exists());
        getJson("/metadata/stations").andExpect(jsonPath("$.features").doesNotExist());
    }
}
