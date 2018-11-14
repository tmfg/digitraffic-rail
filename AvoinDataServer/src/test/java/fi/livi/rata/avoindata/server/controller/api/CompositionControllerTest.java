package fi.livi.rata.avoindata.server.controller.api;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.CompositionFactory;

public class CompositionControllerTest extends MockMvcBaseTest {
    @Autowired
    private CompositionFactory compositionFactory;

    @Test
    @Transactional
    public void versionSearchShouldWork() throws Exception {
        compositionFactory.create();

        assertLength("/compositions?version=0", 1);
        assertLength("/compositions?version=1",0);
        assertLength("/compositions?version=2",0);
    }


}