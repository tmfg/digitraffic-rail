package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainLocationFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Transactional
public class TrainLocationV2ControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainLocationFactory trainLocationFactory;

    @Test
    public void baseAttributesShouldBeCorrect() throws Exception {
        final TrainLocation trainLocation = trainLocationFactory.createTrainLocation();

        final ResultActions r1 = getJson("/train-locations/latest", "v2");

        r1.andExpect(jsonPath("$.length()").value(1));
        r1.andExpect(jsonPath("$[0].trainNumber").value(1L));
        r1.andExpect(jsonPath("$[0].departureDate").value(trainLocation.trainLocationId.departureDate.toString()));

        r1.andExpect(jsonPath("$[0].speed").value(trainLocation.speed));

        r1.andExpect(jsonPath("$[0].location.coordinates[0]").doesNotExist());
        r1.andExpect(jsonPath("$[0].location[0]").value(trainLocation.location.getX()));
        r1.andExpect(jsonPath("$[0].location[1]").value(trainLocation.location.getY()));
    }
}
