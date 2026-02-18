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

    @Test
    public void versionParameterShouldFilterTrainByDepartureDateAndNumber() throws Exception {
        final LocalDate someDate = DateProvider.dateInHelsinki();

        // Create train with version 10
        trainFactory.createBaseTrain(new TrainId(1L, someDate), 10L);

        // Version 0 should return the train (version 10 > 0)
        getJson(String.format("/trains/%s/1?version=0", someDate))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(10));

        // Version 9 should return the train (version 10 > 9)
        getJson(String.format("/trains/%s/1?version=9", someDate))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(10));

        // Version 10 should NOT return the train (version 10 is NOT > 10)
        getJson(String.format("/trains/%s/1?version=10", someDate))
                .andExpect(jsonPath("$.length()").value(0));

        // Version 11 should NOT return the train (version 10 is NOT > 11)
        getJson(String.format("/trains/%s/1?version=11", someDate))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void versionParameterShouldFilterTrainWithIncludeDeleted() throws Exception {
        final LocalDate someDate = DateProvider.dateInHelsinki();

        // Create deleted train with version 5
        final Train deletedTrain = trainFactory.createBaseTrain(new TrainId(1L, someDate), 5L);
        deletedTrain.deleted = true;
        trainRepository.save(deletedTrain);

        // Version 4, include_deleted=true should return the train
        getJson(String.format("/trains/%s/1?version=4&include_deleted=true", someDate))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(5));

        // Version 5, include_deleted=true should NOT return the train (version not greater)
        getJson(String.format("/trains/%s/1?version=5&include_deleted=true", someDate))
                .andExpect(jsonPath("$.length()").value(0));

        // Version 4, include_deleted=false should NOT return the train (it's deleted)
        getJson(String.format("/trains/%s/1?version=4&include_deleted=false", someDate))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    public void defaultVersionShouldReturnTrain() throws Exception {
        final LocalDate someDate = DateProvider.dateInHelsinki();

        trainFactory.createBaseTrain(new TrainId(1L, someDate), 1L);

        // Without version parameter (defaults to 0), should return the train
        getJson(String.format("/trains/%s/1", someDate))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value(1));
    }
}
