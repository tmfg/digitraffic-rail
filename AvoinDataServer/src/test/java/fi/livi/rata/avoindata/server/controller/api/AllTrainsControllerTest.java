package fi.livi.rata.avoindata.server.controller.api;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;

public class AllTrainsControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainController trainController;

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

    @Test
    @Transactional
    public void oldestTrainsShouldBeIncludedAndOrderedByTrainNumber() throws Exception {
        int maxAnnouncedTrains = trainController.MAX_ANNOUNCED_TRAINS;
        trainController.MAX_ANNOUNCED_TRAINS = 5;

        for (long i = 1; i <= 10; i++) {
            Train baseTrain = trainFactory.createBaseTrain(new TrainId(i, LocalDate.now().plusDays(100)));
            baseTrain.version = 10 - i + 1;
        }

        final ResultActions r1 = getJson("/all-trains?version=0");
        r1.andExpect(jsonPath("$.length()").value(5));

        r1.andExpect(jsonPath("$[0].version").value(5));
        r1.andExpect(jsonPath("$[1].version").value(4));
        r1.andExpect(jsonPath("$[2].version").value(3));
        r1.andExpect(jsonPath("$[3].version").value(2));
        r1.andExpect(jsonPath("$[4].version").value(1));

        r1.andExpect(jsonPath("$[0].trainNumber").value(6));
        r1.andExpect(jsonPath("$[1].trainNumber").value(7));
        r1.andExpect(jsonPath("$[2].trainNumber").value(8));
        r1.andExpect(jsonPath("$[3].trainNumber").value(9));
        r1.andExpect(jsonPath("$[4].trainNumber").value(10));

        final ResultActions r2 = getJson("/all-trains?version=5");
        r2.andExpect(jsonPath("$.length()").value(5));

        r2.andExpect(jsonPath("$[0].version").value(10));
        r2.andExpect(jsonPath("$[1].version").value(9));
        r2.andExpect(jsonPath("$[2].version").value(8));
        r2.andExpect(jsonPath("$[3].version").value(7));
        r2.andExpect(jsonPath("$[4].version").value(6));

        r2.andExpect(jsonPath("$[0].trainNumber").value(1));
        r2.andExpect(jsonPath("$[1].trainNumber").value(2));
        r2.andExpect(jsonPath("$[2].trainNumber").value(3));
        r2.andExpect(jsonPath("$[3].trainNumber").value(4));
        r2.andExpect(jsonPath("$[4].trainNumber").value(5));

        trainController.MAX_ANNOUNCED_TRAINS = maxAnnouncedTrains;
    }
}
