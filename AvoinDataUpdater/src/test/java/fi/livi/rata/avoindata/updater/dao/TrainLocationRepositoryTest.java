package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.BaseTest;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

public class TrainLocationRepositoryTest extends BaseTest {
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void findLatestNoResults() {
        Assertions.assertThatCollection(trainLocationRepository.findLatest(ZonedDateTime.now())).isEmpty();
    }

    @Test
    public void findLatestForPassengerTrainsNoResults() {
        Assertions.assertThatCollection(trainLocationRepository.findLatestForPassengerTrains(ZonedDateTime.now())).isEmpty();
    }

    @Test
    @Transactional
    public void deleteOldestOverTwoYearsOldNoRows() {
        Assertions.assertThat(trainLocationRepository.deleteOldestOverTwoYearsOld()).isZero();
    }

    @Test
    @Transactional
    public void deleteOldestOverTwoYearsOldDeletesOnlyOldRows() {
        final ZonedDateTime now = ZonedDateTime.now();
        final TrainLocation veryOld = location(1L, now.minusYears(3));
        final TrainLocation justOverTwoYears = location(2L, now.minusYears(2).minusDays(2));
        final TrainLocation justUnderTwoYears = location(3L, now.minusYears(2).plusDays(2));
        final TrainLocation recent = location(4L, now.minusHours(1));
        trainLocationRepository.saveAllAndFlush(List.of(veryOld, justOverTwoYears, justUnderTwoYears, recent));

        final int deleted = trainLocationRepository.deleteOldestOverTwoYearsOld();

        Assertions.assertThat(deleted).isEqualTo(2);
        Assertions.assertThat(remainingIds())
                .containsExactlyInAnyOrder(justUnderTwoYears.id, recent.id);
    }

    @Test
    @Transactional
    public void deleteOldestOverTwoYearsOldIsIdempotent() {
        final ZonedDateTime now = ZonedDateTime.now();
        final TrainLocation old = location(1L, now.minusYears(3));
        final TrainLocation recent = location(2L, now.minusDays(1));
        trainLocationRepository.saveAllAndFlush(List.of(old, recent));

        Assertions.assertThat(trainLocationRepository.deleteOldestOverTwoYearsOld()).isEqualTo(1);
        Assertions.assertThat(trainLocationRepository.deleteOldestOverTwoYearsOld()).isZero();
        Assertions.assertThat(remainingIds()).containsExactly(recent.id);
    }

    private List<Long> remainingIds() {
        entityManager.clear();
        return trainLocationRepository.findAll().stream().map(tl -> tl.id).toList();
    }

    private TrainLocation location(final long trainNumber, final ZonedDateTime timestamp) {
        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.trainLocationId = new TrainLocationId(trainNumber, timestamp.toLocalDate(), timestamp);
        trainLocation.location = geometryFactory.createPoint(new Coordinate(24.94, 60.17));
        trainLocation.speed = 80;
        trainLocation.accuracy = 10;
        return trainLocation;
    }
}
