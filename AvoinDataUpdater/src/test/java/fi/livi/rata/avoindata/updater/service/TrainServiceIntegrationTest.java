package fi.livi.rata.avoindata.updater.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.cause.CauseRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

public class TrainServiceIntegrationTest extends BaseTest {

    @Autowired
    private TrainPersistService trainService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Autowired
    private CauseRepository causeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testAddTrains() throws Exception {
        testDataService.createTrainsFromResource("trainsSingle.json");
        final List<Train> trains = trainRepository.findAll();
        Assert.assertEquals(1, trains.size());
        final Train train = trains.get(0);
        Assert.assertEquals(new Long(10600L), train.id.trainNumber);
        Assert.assertEquals(LocalDate.of(2014, 12, 10), train.id.departureDate);
        Assert.assertEquals(10, train.operator.operatorUICCode);
        Assert.assertEquals("vr", train.operator.operatorShortCode);
        Assert.assertEquals(52L, train.trainTypeId);
        Assert.assertEquals("V", train.commuterLineID);
        Assert.assertFalse(train.runningCurrently);
        Assert.assertFalse(train.cancelled);
        Assert.assertEquals(46, train.timeTableRows.size());
        Assert.assertEquals(0, train.timeTableRows.stream().mapToLong(x -> x.causes.size()).sum());
    }

    @Test
    @Transactional
    public void testRemoveTrainsCascadesToChildren() throws Exception {
        final int AMOUNT_OF_TRAINS = 1;
        final int AMOUNT_OF_TIMETABLEROWS = 46;
        final int AMOUNT_OF_CAUSES = 0;

        testDataService.createTrainsFromResource("trainsSingle.json");

        Assert.assertEquals(String.format("Should contain %d individual trains", AMOUNT_OF_TRAINS), AMOUNT_OF_TRAINS,
                trainRepository.count());
        Assert.assertEquals(String.format("Should contain %d individual timeTableRows", AMOUNT_OF_TIMETABLEROWS), AMOUNT_OF_TIMETABLEROWS,
                timeTableRowRepository.count());
        Assert.assertEquals(String.format("Should contain %d individual causes", AMOUNT_OF_CAUSES), AMOUNT_OF_CAUSES,
                causeRepository.count());

        trainService.clearEntities();

        Assert.assertEquals("Databse should contain no trains", 0, trainRepository.count());
        Assert.assertEquals("Database should contain no timeTableRows", 0, timeTableRowRepository.count());
        Assert.assertEquals("Database should contain no causes", 0, causeRepository.count());
    }

    @Test
    public void testUpdateTrains() throws Exception {
        final List<Train> trains = testDataService.parseTrains("trainsSingle.json");
        final Train originalTrain = trains.get(0);
        originalTrain.timeTableRows.forEach(x -> x.actualTime = null);
        trainService.updateEntities(trains);
        final Train trainFromRepository = trainRepository.findByDepartureDateAndTrainNumber(originalTrain.id.departureDate,
                originalTrain.id.trainNumber, false);
        Assert.assertEquals(1, trainRepository.count());
        List<TimeTableRow> timeTableRowsWithActualTime = trainFromRepository.timeTableRows.stream().filter(x -> x.actualTime != null)
                .collect(Collectors.toList());
        Assert.assertTrue(timeTableRowsWithActualTime.isEmpty());

        final List<Train> trainsUpdated = testDataService.parseTrains("trainsSingle.json");
        trainService.updateEntities(trainsUpdated);
        Assert.assertEquals(1, trainRepository.count());
        final Train updatedTrainFromRepository = trainRepository.findByDepartureDateAndTrainNumber(originalTrain.id.departureDate,
                originalTrain.id.trainNumber, false);
        timeTableRowsWithActualTime = updatedTrainFromRepository.timeTableRows.stream().filter(x -> x.actualTime != null).collect(
                Collectors.toList());
        Assert.assertFalse(timeTableRowsWithActualTime.isEmpty());
        Assert.assertEquals(46, timeTableRowRepository.count());

        trainRepository.deleteAllInBatch();
    }
}
