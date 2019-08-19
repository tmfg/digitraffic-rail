package fi.livi.rata.avoindata.server.controller.mvc;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainLocationFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Transactional
public class TrainLocationControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainLocationFactory trainLocationFactory;
    @Autowired
    private DateProvider dp;

    @Test
    public void baseAttributesShouldBeCorrect() throws Exception {
        final TrainLocation trainLocation = trainLocationFactory.createTrainLocation();

        final ResultActions r1 = getJson("/train-locations/latest");

        r1.andExpect(jsonPath("$.length()").value(1));
        r1.andExpect(jsonPath("$[0].trainNumber").value(1L));
        r1.andExpect(jsonPath("$[0].departureDate").value(trainLocation.trainLocationId.departureDate.toString()));

        r1.andExpect(jsonPath("$[0].speed").value(trainLocation.speed));

        r1.andExpect(jsonPath("$[0].location.coordinates[0]").value(trainLocation.location.getX()));
        r1.andExpect(jsonPath("$[0].location.coordinates[1]").value(trainLocation.location.getY()));
    }

    @Test
    public void trainNumberFilteringShouldWork() throws Exception {
        trainLocationFactory.createTrainLocation();

        getJson("/train-locations/latest/1").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest/2").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void geoJsonWorks() throws Exception {
        trainLocationFactory.createTrainLocation();

        getGeoJson("/train-locations.geojson/latest")
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.length()").value(4))
                .andExpect(jsonPath("$.features[0].properties['trainNumber']").value(1))
                .andExpect(jsonPath("$.features[0].properties['departureDate']").value(dp.dateInHelsinki().toString()))
                .andExpect(jsonPath("$.features[0].properties['speed']").value(100))
                .andExpect(jsonPath("$.features[0].properties['timestamp']").exists());
        getGeoJson("/train-locations.geojson/latest/1").andExpect(jsonPath("$.features.length()").value(1));
        getJson("/train-locations/latest/1").andExpect(jsonPath("$.features").doesNotExist());
    }

    @Test
    public void trainIdFilteringShouldWork() throws Exception {
        LocalDate dateInHelsinki = dp.dateInHelsinki();
        trainLocationFactory.createTrainLocation(new TrainLocationId(1L, dateInHelsinki, dp.nowInHelsinki()));

        getJson(String.format("/train-locations/%s/1", dateInHelsinki)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/train-locations/%s/2", dateInHelsinki)).andExpect(jsonPath("$.length()").value(0));
        getJson(String.format("/train-locations/%s/1", dateInHelsinki.minusDays(1))).andExpect(jsonPath("$.length()").value(0));
        getJson(String.format("/train-locations/%s/1", dateInHelsinki.plusDays(1))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void boundingBoxShouldWork() throws Exception {
        trainLocationFactory.createTrainLocation(); // 20.3, 10.1

        getJson("/train-locations/latest/1?bbox=1,1,70,70").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest/1?bbox=20.0, 10.0,21,11").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest/1?bbox=19.0, 20.0,21,11").andExpect(jsonPath("$.length()").value(0));

        getJson("/train-locations/latest?bbox=1,1,70,70").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest?bbox=20.0, 10.0,21,11").andExpect(jsonPath("$.length()").value(1));
        getJson("/train-locations/latest?bbox=19.0, 11.0,21,11").andExpect(jsonPath("$.length()").value(0));

    }
}
