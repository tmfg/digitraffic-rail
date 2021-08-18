package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;

public class TrainControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainFactory trainFactory;
    @Autowired
    private DateProvider dateProvider;
    @Autowired
    private TrainRepository trainRepository;

    @Test
    public void deletedShouldBeFilteredByDefault() throws Exception {
        LocalDate someDate = dateProvider.dateInHelsinki();

        trainFactory.createBaseTrain(new TrainId(1L, someDate));

        Train deletedTrain = trainFactory.createBaseTrain(new TrainId(2L, someDate));
        deletedTrain.deleted = true;
        trainRepository.save(deletedTrain);

        trainFactory.createBaseTrain(new TrainId(3L, someDate));

        getJson(String.format("/trains/%s/1", someDate)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/trains/%s/2", someDate)).andExpect(jsonPath("$.length()").value(0));
        getJson(String.format("/trains/%s", someDate)).andExpect(jsonPath("$.length()").value(2));

        trainRepository.deleteAll();
    }

    @Test
    public void deletedParameterShouldBeHonored() throws Exception {
        LocalDate someDate = dateProvider.dateInHelsinki();

        trainFactory.createBaseTrain(new TrainId(1L, someDate));

        Train deletedTrain = trainFactory.createBaseTrain(new TrainId(2L, someDate));
        deletedTrain.deleted = true;
        trainRepository.save(deletedTrain);

        trainFactory.createBaseTrain(new TrainId(3L, someDate));

        getJson(String.format("/trains/%s/2?include_deleted=true", someDate)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/trains/%s/2?include_deleted=false", someDate)).andExpect(jsonPath("$.length()").value(0));

        getJson(String.format("/trains/%s/1?include_deleted=true", someDate)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/trains/%s/1?include_deleted=false", someDate)).andExpect(jsonPath("$.length()").value(1));

        getJson(String.format("/trains/%s?include_deleted=true", someDate)).andExpect(jsonPath("$.length()").value(3));
        getJson(String.format("/trains/%s?include_deleted=false", someDate)).andExpect(jsonPath("$.length()").value(2));

        trainRepository.deleteAll();
    }
}
