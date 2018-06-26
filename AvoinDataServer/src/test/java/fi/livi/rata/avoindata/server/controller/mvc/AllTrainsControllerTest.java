package fi.livi.rata.avoindata.server.controller.mvc;


import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class AllTrainsControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainFactory trainFactory;

    @Test
    @Transactional
    public void versionLessThanShouldShow() throws Exception {
        trainFactory.createBaseTrain(new TrainId(1L, LocalDate.now().plusDays(100)));

        final ResultActions r1 = getJson("/all-trains?version=0");
        r1.andExpect(jsonPath("$.length()").value(1));

        final ResultActions r2 = getJson("/all-trains?version=1");
        r2.andExpect(jsonPath("$.length()").value(0));

        final ResultActions r3 = getJson("/all-trains");
        r3.andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Transactional
    public void deletedShouldBeReturned() throws Exception {
        final Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.now().plusDays(100)));
        train.deleted = true;

        final ResultActions r1 = getJson("/all-trains?version=0");
        r1.andExpect(jsonPath("$.length()").value(1));

        final ResultActions r2 = getJson("/all-trains?version=1");
        r2.andExpect(jsonPath("$.length()").value(0));

        final ResultActions r3 = getJson("/all-trains");
        r3.andExpect(jsonPath("$.length()").value(1));
    }
}
