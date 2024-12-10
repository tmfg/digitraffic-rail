package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;

public class ScheduleControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainFactory trainFactory;

    @Test
    @Transactional
    public void routeSearchShouldWork() throws Exception {
        trainFactory.createBaseTrain(new TrainId(1L, DateProvider.dateInHelsinki()));

        //Whole trip
        assertLength("/live-trains/station/HKI/OL",1);

        //Part from start
        assertLength("/live-trains/station/HKI/TPE",1);

        //Part from middle
        assertLength("/live-trains/station/TPE/JY",1);

        //Part from end
        assertLength("/live-trains/station/TPE/OL",1);

        //Start matches, end does not
        assertException("/live-trains/station/HKI/TKU","TRAIN_NOT_FOUND");

        //End matches, start does not
        assertException("/live-trains/station/OL/TKU","TRAIN_NOT_FOUND");

        //Nothing matches
        assertException("/live-trains/station/TKU/JOE","TRAIN_NOT_FOUND");
    }

    @Test
    @Transactional
    public void attributesPresent() throws Exception {
        trainFactory.createBaseTrain();



        getJson("/live-trains/station/HKI/OL")
                .andExpect(jsonPath("$[0].trainNumber").value("51"))
                .andExpect(jsonPath("$[0].departureDate").value(DateProvider.dateInHelsinki().toString()))
                .andExpect(jsonPath("$[0].operatorUICCode").value("1"))
                .andExpect(jsonPath("$[0].operatorShortCode").value("test"))
                .andExpect(jsonPath("$[0].commuterLineID").value("Z"))
                .andExpect(jsonPath("$[0].runningCurrently").value("true"))
                .andExpect(jsonPath("$[0].cancelled").value("false"))
                .andExpect(jsonPath("$[0].version").value("1"))
                .andExpect(jsonPath("$[0].timeTableRows").isNotEmpty())
                .andExpect(jsonPath("$[0].timeTableRows[0].actualTime").isNotEmpty())
        ;
    }
}