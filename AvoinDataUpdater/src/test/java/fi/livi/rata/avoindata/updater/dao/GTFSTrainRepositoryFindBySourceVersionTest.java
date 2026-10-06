package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class GTFSTrainRepositoryFindBySourceVersionTest extends BaseTest {
    @Autowired
    private GTFSTrainRepository gtfsTrainRepository;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainRepository trainRepository;

    private static final AtomicLong trainNumberSequence = new AtomicLong(1000L);

    private Train createTrainWithProperties(final long categoryId, final long typeId, final LocalDate departureDate, final long sourceVersion) {
        final TrainId trainId = new TrainId(trainNumberSequence.incrementAndGet(), departureDate);
        final Train t = trainFactory.createBaseTrain(trainId);
        t.trainCategoryId = categoryId;
        t.trainTypeId = typeId;
        t.sourceVersion = sourceVersion;
        trainRepository.save(t);
        return t;
    }

    /**
     * Test that finds trains with source version greater than the given version
     */
    @Test
    public void findBySourceVersionGreaterThanBasic() {
        final LocalDate today = LocalDate.now();
        final Train t1 = createTrainWithProperties(1, 1, today, 150L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).isNotEmpty();
        assertThat(result).anySatisfy(train -> {
            assertThat(train.id.trainNumber).isEqualTo(t1.id.trainNumber);
            assertThat(train.sourceVersion).isGreaterThan(100L);
        });
    }

    /**
     * Test that only includes trains from category Commuter (1) or Long-distance (2)
     */
    @Test
    public void findBySourceVersionGreaterThanOnlyCategoriesOneAndTwo() {
        final LocalDate today = LocalDate.now();

        final Train t1 = createTrainWithProperties(1, 1, today, 150L);  // Commuter
        final Train t2 = createTrainWithProperties(2, 1, today, 150L);  // Long-distance
        final Train t3 = createTrainWithProperties(3, 1, today, 150L);  // Other category

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).hasSize(2);
        assertThat(result).noneMatch(train -> train.id.trainNumber == t3.id.trainNumber);
        assertThat(result).allMatch(train -> train.trainCategoryId == 1 || train.trainCategoryId == 2);
    }

    /**
     * Test that excludes trains with type V (81), HV (52), or MV (53)
     */
    @Test
    public void findBySourceVersionGreaterThanExcludesSpecialTypes() {
        final LocalDate today = LocalDate.now();

        final Train t1 = createTrainWithProperties(1, 1, today, 150L);    // Allowed type
        final Train tV = createTrainWithProperties(1, 81, today, 150L);   // V type
        final Train tHV = createTrainWithProperties(1, 52, today, 150L);  // HV type
        final Train tMV = createTrainWithProperties(1, 53, today, 150L);  // MV type

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).hasSize(1);
        assertThat(result).anySatisfy(train -> assertThat(train.id.trainNumber).isEqualTo(t1.id.trainNumber));
        assertThat(result).noneMatch(train -> train.trainTypeId == 81 || train.trainTypeId == 52 || train.trainTypeId == 53);
    }

    /**
     * Test that only includes trains from today or yesterday
     */
    @Test
    public void findBySourceVersionGreaterThanOnlyTodayAndYesterday() {
        final LocalDate today = LocalDate.now();
        final LocalDate yesterday = today.minusDays(1);
        final LocalDate twoDaysAgo = today.minusDays(2);

        final Train tToday = createTrainWithProperties(1, 1, today, 150L);
        final Train tYesterday = createTrainWithProperties(1, 1, yesterday, 150L);
        final Train tOld = createTrainWithProperties(1, 1, twoDaysAgo, 150L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).hasSize(2);
        final List<LocalDate> resultDates = result.stream().map(t -> t.id.departureDate).toList();
        assertThat(resultDates).contains(today, yesterday);
        assertThat(resultDates).doesNotContain(twoDaysAgo);
    }

    /**
     * Test that only finds trains with source version greater than the provided version
     */
    @Test
    public void findBySourceVersionGreaterThanVersionComparison() {
        final LocalDate today = LocalDate.now();

        final Train t150 = createTrainWithProperties(1, 1, today, 150L);
        final Train t100 = createTrainWithProperties(2, 1, today, 100L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(99L, today);
        assertThat(result).hasSize(2);

        final List<GTFSTrain> result2 = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);
        assertThat(result2).hasSize(1);
        assertThat(result2.get(0).sourceVersion).isGreaterThan(100L);
    }

    /**
     * Test with no matching trains
     */
    @Test
    public void findBySourceVersionGreaterThanNoMatches() {
        final LocalDate today = LocalDate.now();

        final Train t = createTrainWithProperties(1, 1, today, 100L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).isEmpty();
    }

    /**
     * Test with empty database
     */
    @Test
    public void findBySourceVersionGreaterThanEmptyTable() {
        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(0L, LocalDate.now());

        assertThat(result).isEmpty();
    }

    /**
     * Test with timezone-aware dates (today and yesterday)
     * This test verifies that the method correctly handles date boundaries
     * based on the provided date parameter
     */
    @Test
    public void findBySourceVersionGreaterThanTimezoneHandling() {
        final LocalDate today = LocalDate.now();
        final LocalDate yesterday = today.minusDays(1);
        final LocalDate tomorrow = today.plusDays(1);

        final Train tToday = createTrainWithProperties(1, 1, today, 150L);
        final Train tYesterday = createTrainWithProperties(2, 1, yesterday, 150L);
        final Train tTomorrow = createTrainWithProperties(1, 1, tomorrow, 150L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(t -> t.id.departureDate).toList())
                .containsExactlyInAnyOrder(today, yesterday)
                .doesNotContain(tomorrow);
    }

    /**
     * Test with all allowed categories and types combinations
     */
    @Test
    public void findBySourceVersionGreaterThanCombinations() {
        final LocalDate today = LocalDate.now();

        final Train t1 = createTrainWithProperties(1, 10, today, 150L);  // Category 1, Type 10
        final Train t2 = createTrainWithProperties(1, 20, today, 150L);  // Category 1, Type 20
        final Train t3 = createTrainWithProperties(2, 10, today, 150L);  // Category 2, Type 10
        final Train t4 = createTrainWithProperties(2, 30, today, 150L);  // Category 2, Type 30

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, today);

        assertThat(result).hasSize(4);
        assertThat(result).allMatch(train -> train.trainCategoryId == 1 || train.trainCategoryId == 2);
        assertThat(result).noneMatch(train -> train.trainTypeId == 81 || train.trainTypeId == 52 || train.trainTypeId == 53);
    }

    /**
     * Test filtering by source version with edge case: version 0
     */
    @Test
    public void findBySourceVersionGreaterThanVersionZero() {
        final LocalDate today = LocalDate.now();

        final Train t1 = createTrainWithProperties(1, 1, today, 1L);
        final Train t2 = createTrainWithProperties(1, 1, today, 100L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(0L, today);

        assertThat(result).hasSize(2);
    }

    /**
     * Test that the date parameter effectively defines the date range (today and yesterday)
     */
    @Test
    public void findBySourceVersionGreaterThanWithSpecificDate() {
        final LocalDate targetDate = LocalDate.of(2026, 10, 3);
        final LocalDate targetDateMinus1 = targetDate.minusDays(1);
        final LocalDate targetDatePlus1 = targetDate.plusDays(1);

        final Train tOnTarget = createTrainWithProperties(1, 1, targetDate, 150L);
        final Train tMinusOne = createTrainWithProperties(1, 1, targetDateMinus1, 150L);
        final Train tPlusOne = createTrainWithProperties(1, 1, targetDatePlus1, 150L);

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionGreaterThan(100L, targetDate);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(t -> t.id.departureDate).toList())
                .containsExactlyInAnyOrder(targetDate, targetDateMinus1)
                .doesNotContain(targetDatePlus1);
    }
}

