package fi.livi.rata.avoindata.updater.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
        Assertions.assertEquals(1, trains.size());
        final Train train = trains.get(0);
        Assertions.assertEquals(10600L, train.id.trainNumber);
        Assertions.assertEquals(LocalDate.of(2014, 12, 10), train.id.departureDate);
        Assertions.assertEquals(10, train.operator.operatorUICCode);
        Assertions.assertEquals("vr", train.operator.operatorShortCode);
        Assertions.assertEquals(52L, train.trainTypeId);
        Assertions.assertEquals("V", train.commuterLineID);
        Assertions.assertFalse(train.runningCurrently);
        Assertions.assertFalse(train.cancelled);
        Assertions.assertEquals(46, train.timeTableRows.size());
        Assertions.assertEquals(0, train.timeTableRows.stream().mapToLong(x -> x.causes.size()).sum());
    }

    @Test
    @Transactional
    public void testRemoveTrainsCascadesToChildren() throws Exception {
        final int AMOUNT_OF_TRAINS = 1;
        final int AMOUNT_OF_TIMETABLEROWS = 46;
        final int AMOUNT_OF_CAUSES = 0;

        testDataService.createTrainsFromResource("trainsSingle.json");

        Assertions.assertEquals(AMOUNT_OF_TRAINS, trainRepository.count(),
                String.format("Should contain %d individual trains", AMOUNT_OF_TRAINS));
        Assertions.assertEquals(AMOUNT_OF_TIMETABLEROWS, timeTableRowRepository.count(),
                String.format("Should contain %d individual timeTableRows", AMOUNT_OF_TIMETABLEROWS));
        Assertions.assertEquals(AMOUNT_OF_CAUSES, causeRepository.count(),
                String.format("Should contain %d individual causes", AMOUNT_OF_CAUSES));

        trainService.clearEntities();

        Assertions.assertEquals(0, trainRepository.count(), "Databse should contain no trains");
        Assertions.assertEquals(0, timeTableRowRepository.count(), "Database should contain no timeTableRows");
        Assertions.assertEquals(0, causeRepository.count(), "Database should contain no causes");
    }

    @Test
    public void testUpdateTrains() throws Exception {
        final List<Train> trains = testDataService.parseTrains("trainsSingle.json");
        final Train originalTrain = trains.get(0);
        originalTrain.timeTableRows.forEach(x -> x.actualTime = null);
        trainService.updateEntities(trains);
        final Train trainFromRepository = trainRepository.findByDepartureDateAndTrainNumber(originalTrain.id.departureDate,
                originalTrain.id.trainNumber, false);
        Assertions.assertEquals(1, trainRepository.count());
        List<TimeTableRow> timeTableRowsWithActualTime = trainFromRepository.timeTableRows.stream().filter(x -> x.actualTime != null)
                .collect(Collectors.toList());
        Assertions.assertTrue(timeTableRowsWithActualTime.isEmpty());

        final List<Train> trainsUpdated = testDataService.parseTrains("trainsSingle.json");
        trainService.updateEntities(trainsUpdated);
        Assertions.assertEquals(1, trainRepository.count());
        final Train updatedTrainFromRepository = trainRepository.findByDepartureDateAndTrainNumber(originalTrain.id.departureDate,
                originalTrain.id.trainNumber, false);
        timeTableRowsWithActualTime = updatedTrainFromRepository.timeTableRows.stream().filter(x -> x.actualTime != null).collect(
                Collectors.toList());
        Assertions.assertFalse(timeTableRowsWithActualTime.isEmpty());
        Assertions.assertEquals(46, timeTableRowRepository.count());

        trainRepository.deleteAllInBatch();
    }
}
