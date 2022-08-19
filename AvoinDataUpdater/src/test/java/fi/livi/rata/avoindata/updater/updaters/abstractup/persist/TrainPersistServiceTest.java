package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import java.util.HashSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;


public class TrainPersistServiceTest extends BaseTest {
    @Autowired
    private TrainPersistService trainPersistService;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainRepository trainRepository;
    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @BeforeEach
    @AfterEach
    public void cleanDatabase() {
        trainRepository.deleteAllInBatch();
        timeTableRowRepository.deleteAllInBatch();
    }

    @Test
    public void addShouldBeOkay() {

        final Train train1 = trainFactory.createBaseTrain();

        for (final TimeTableRow timeTableRow : train1.timeTableRows) {
            timeTableRow.trainReadies = new HashSet<>();
            timeTableRow.causes = new HashSet<>();
        }

        cleanDatabase();

        trainPersistService.addEntities(Lists.newArrayList(train1));

        Assertions.assertNotNull(trainRepository.findById(train1.id).orElse(null));
    }

    @Test
    public void addThenUpdateShouldBeOkay() {

        final Train train1 = trainFactory.createBaseTrain();

        for (final TimeTableRow timeTableRow : train1.timeTableRows) {
            timeTableRow.trainReadies = new HashSet<>();
            timeTableRow.causes = new HashSet<>();
        }

        cleanDatabase();

        trainPersistService.addEntities(Lists.newArrayList(train1));

        final Train addedTrain = trainRepository.findById(train1.id).orElse(null);
        Assertions.assertNotNull(addedTrain);
        Assertions.assertEquals(false, addedTrain.cancelled);

        train1.cancelled = true;

        trainPersistService.updateEntities(Lists.newArrayList(train1));

        final Train updatedTrain = trainRepository.findById(train1.id).orElse(null);
        Assertions.assertNotNull(updatedTrain);
        Assertions.assertEquals(true, updatedTrain.cancelled);


    }
}