package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

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
    private TrainRepository trainRepository;

    @AfterEach
    public void cleanUp() {
        trainRepository.deleteAll();
    }

    @Test
    public void deletedShouldBeFilteredByDefault() throws Exception {
        final LocalDate someDate = DateProvider.dateInHelsinki();

        trainFactory.createBaseTrain(new TrainId(1L, someDate));

        final Train deletedTrain = trainFactory.createBaseTrain(new TrainId(2L, someDate));
        deletedTrain.deleted = true;
        trainRepository.save(deletedTrain);

        trainFactory.createBaseTrain(new TrainId(3L, someDate));

        getJson(String.format("/trains/%s/1", someDate)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/trains/%s/2", someDate)).andExpect(jsonPath("$.length()").value(0));
        getJson(String.format("/trains/%s", someDate)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void deletedParameterShouldBeHonored() throws Exception {
        final LocalDate someDate = DateProvider.dateInHelsinki();

        trainFactory.createBaseTrain(new TrainId(1L, someDate));

        final Train deletedTrain = trainFactory.createBaseTrain(new TrainId(2L, someDate));
        deletedTrain.deleted = true;
        trainRepository.save(deletedTrain);

        trainFactory.createBaseTrain(new TrainId(3L, someDate));

        getJson(String.format("/trains/%s/2?include_deleted=true", someDate)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/trains/%s/2?include_deleted=false", someDate)).andExpect(jsonPath("$.length()").value(0));

        getJson(String.format("/trains/%s/1?include_deleted=true", someDate)).andExpect(jsonPath("$.length()").value(1));
        getJson(String.format("/trains/%s/1?include_deleted=false", someDate)).andExpect(jsonPath("$.length()").value(1));

        getJson(String.format("/trains/%s?include_deleted=true", someDate)).andExpect(jsonPath("$.length()").value(3));
        getJson(String.format("/trains/%s?include_deleted=false", someDate)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void shouldFindTrainByNumberWhenTrainReadyMessagesExist() throws Exception {
        final Train train = trainFactory.createBaseTrainWithTrainReadyMessages();
        trainRepository.save(train);

        getJson(String.format("/trains/latest/%d", train.id.trainNumber))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainNumber").value(train.id.trainNumber));
    }

    @Sql(scripts = "/trains/insert_over_2500_trains_one_version.sql", config = @SqlConfig(separator = "$$") )
    @Test
    public void singleVersionExceedingRowLimitIsFiltered() throws Exception {
        getJson(String.format("/trains?version=%s", 0))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Sql(scripts = "/trains/insert_over_2500_trains_two_versions.sql", config = @SqlConfig(separator = "$$") )
    @Test
    public void partialVersionIsFilteredWhenRowLimitIsExceeded() throws Exception {
        getJson(String.format("/trains?version=%s", 0))
                .andExpect(jsonPath("$.length()").value(2499))
                .andExpect(jsonPath("$[0].version").value(1));
    }
}
