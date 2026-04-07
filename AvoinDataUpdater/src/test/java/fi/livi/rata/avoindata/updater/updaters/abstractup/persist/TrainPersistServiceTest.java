package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainApiConstants;
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

    // --- Version chunking tests ---

    @Test
    public void updateEntitiesBelowChunkLimitAllGetSameVersion() {
        final long previousMax = trainPersistService.getMaxApiVersion();
        final List<Train> trains = createUnpersistedTrains(10);

        trainPersistService.updateEntities(trains);

        final long expectedVersion = previousMax + 1;
        final List<Train> saved = trainRepository.findAll();
        Assertions.assertEquals(10, saved.size());
        for (final Train t : saved) {
            Assertions.assertEquals(expectedVersion, t.version, "All trains in one chunk must share the same version");
        }
        Assertions.assertEquals(expectedVersion, trainPersistService.getMaxApiVersion());
    }

    @Test
    public void updateEntitiesExactlyChunkSizeAllGetSameVersion() {
        final long previousMax = trainPersistService.getMaxApiVersion();
        final List<Train> trains = createUnpersistedTrains(TrainApiConstants.MAX_TRAINS_PER_VERSION);

        trainPersistService.updateEntities(trains);

        final long expectedVersion = previousMax + 1;
        Assertions.assertEquals(expectedVersion, trainPersistService.getMaxApiVersion());
        final long distinctVersionCount = trainRepository.findAll().stream()
                .map(t -> t.version).distinct().count();
        Assertions.assertEquals(1, distinctVersionCount, "Exactly MAX_TRAINS_PER_VERSION trains must fit in one version");
    }

    @Test
    public void updateEntitiesOneOverChunkLimitSplitsIntoTwoVersions() {
        final long previousMax = trainPersistService.getMaxApiVersion();
        final List<Train> trains = createUnpersistedTrains(TrainApiConstants.MAX_TRAINS_PER_VERSION + 1);

        trainPersistService.updateEntities(trains);

        final long firstVersion = previousMax + 1;
        final long secondVersion = previousMax + 2;
        Assertions.assertEquals(secondVersion, trainPersistService.getMaxApiVersion());

        final List<Train> saved = trainRepository.findAll();
        final long countFirst = saved.stream().filter(t -> t.version == firstVersion).count();
        final long countSecond = saved.stream().filter(t -> t.version == secondVersion).count();
        Assertions.assertEquals(TrainApiConstants.MAX_TRAINS_PER_VERSION, countFirst,
                "First chunk must contain exactly MAX_TRAINS_PER_VERSION trains");
        Assertions.assertEquals(1, countSecond, "Second chunk must contain the single remaining train");
    }

    @Test
    public void updateEntitiesTwoFullChunksSplitsIntoTwoVersions() {
        final long previousMax = trainPersistService.getMaxApiVersion();
        final int twoChunks = TrainApiConstants.MAX_TRAINS_PER_VERSION * 2;
        final List<Train> trains = createUnpersistedTrains(twoChunks);

        trainPersistService.updateEntities(trains);

        final long firstVersion = previousMax + 1;
        final long secondVersion = previousMax + 2;
        Assertions.assertEquals(secondVersion, trainPersistService.getMaxApiVersion());

        final List<Train> saved = trainRepository.findAll();
        final long countFirst = saved.stream().filter(t -> t.version == firstVersion).count();
        final long countSecond = saved.stream().filter(t -> t.version == secondVersion).count();
        Assertions.assertEquals(TrainApiConstants.MAX_TRAINS_PER_VERSION, countFirst);
        Assertions.assertEquals(TrainApiConstants.MAX_TRAINS_PER_VERSION, countSecond);
    }

    @Test
    public void sequentialCallsProduceStrictlyIncreasingVersions() {
        final long previousMax = trainPersistService.getMaxApiVersion();

        trainPersistService.updateEntities(createUnpersistedTrains(3, LocalDate.now().plusDays(10)));
        final long versionAfterFirst = trainPersistService.getMaxApiVersion();
        Assertions.assertEquals(previousMax + 1, versionAfterFirst);

        trainPersistService.updateEntities(createUnpersistedTrains(3, LocalDate.now().plusDays(11)));
        final long versionAfterSecond = trainPersistService.getMaxApiVersion();
        Assertions.assertEquals(previousMax + 2, versionAfterSecond);
    }

    @Test
    public void addEntitiesAlsoAssignsVersionsInChunks() {
        final long previousMax = trainPersistService.getMaxApiVersion();
        final List<Train> trains = createUnpersistedTrains(TrainApiConstants.MAX_TRAINS_PER_VERSION + 1);

        trainPersistService.addEntities(trains);

        final long firstVersion = previousMax + 1;
        final long secondVersion = previousMax + 2;
        Assertions.assertEquals(secondVersion, trainPersistService.getMaxApiVersion());

        final List<Train> saved = trainRepository.findAll();
        final long countFirst = saved.stream().filter(t -> t.version == firstVersion).count();
        final long countSecond = saved.stream().filter(t -> t.version == secondVersion).count();
        Assertions.assertEquals(TrainApiConstants.MAX_TRAINS_PER_VERSION, countFirst);
        Assertions.assertEquals(1, countSecond);
    }

    @Test
    public void sourceVersionIsPersistedToDatabase() {
        final List<Train> trains = createUnpersistedTrains(2);
        trains.forEach(t -> t.sourceVersion = 12345L);

        trainPersistService.updateEntities(trains);

        final List<Train> saved = trainRepository.findAll();
        for (final Train t : saved) {
            Assertions.assertEquals(12345L, t.sourceVersion,
                    "sourceVersion must be persisted to DB and must not be overwritten by version assignment");
        }
    }

    @Test
    public void getMaxSourceVersionReturnsMaxStoredSourceVersion() {
        Assertions.assertEquals(0L, trainPersistService.getMaxSourceVersion(), "Should return 0 when table is empty");

        final List<Train> batch1 = createUnpersistedTrains(2, LocalDate.now().plusDays(20));
        batch1.forEach(t -> t.sourceVersion = 100L);
        trainPersistService.updateEntities(batch1);

        final List<Train> batch2 = createUnpersistedTrains(2, LocalDate.now().plusDays(21));
        batch2.forEach(t -> t.sourceVersion = 300L);
        trainPersistService.updateEntities(batch2);

        Assertions.assertEquals(300L, trainPersistService.getMaxSourceVersion());
    }

    // --- Helpers ---

    private List<Train> createUnpersistedTrains(final int count) {
        return createUnpersistedTrains(count, LocalDate.now().plusDays(100));
    }

    private List<Train> createUnpersistedTrains(final int count, final LocalDate departureDate) {
        final List<Train> trains = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trains.add(trainFactory.createUnpersistedTrain(1000L + i, departureDate));
        }
        return trains;
    }
}

