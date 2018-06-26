package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
public class ScheduleControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainFactory trainFactory;
    @Autowired
    private DateProvider dateProvider;

    @Test
    @Transactional
    public void routeSearchShouldWork() throws Exception {
        trainFactory.createBaseTrain(new TrainId(1L, dateProvider.dateInHelsinki()));

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
}