package fi.livi.rata.avoindata.updater.service.miku;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Iterables;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.ForecastFactory;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;

public class ForecastMergingServiceTest extends BaseTest {
    @Autowired
    private ForecastMergingService forecastMergingService;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private ForecastFactory forecastFactory;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;
    @Autowired
    private DateProvider dp;

    @Test
    @Transactional
    public void emptyVsForecastShouldEqualForecast() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        Forecast forecast = forecastFactory.create(Iterables.getLast(train.timeTableRows), 5);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);


        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        assertTimeTableRow(updatedTrain.timeTableRows.get(0), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(1), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(2), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(3), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(4), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(5), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(7), null, 5);
    }


    @Test
    @Transactional
    public void emptyVsTwoForecastShouldEqualForecast() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(0), 5);
        Forecast forecast2 = forecastFactory.create(train.timeTableRows.get(4), 10);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);


        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast, forecast2));

        assertTimeTableRow(updatedTrain.timeTableRows.get(0), null, 5);
        assertTimeTableRow(updatedTrain.timeTableRows.get(1), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(2), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(3), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(4), null, 10);
        assertTimeTableRow(updatedTrain.timeTableRows.get(5), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(7), null, null);
    }

    @Test
    @Transactional
    public void liveEstimateVsForecastShouldEqualForecast() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        final TimeTableRow lastTimeTableRow = Iterables.getLast(train.timeTableRows);
        lastTimeTableRow.liveEstimateTime = lastTimeTableRow.scheduledTime.plusMinutes(5);
        lastTimeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;

        Forecast forecast = forecastFactory.create(lastTimeTableRow, 10);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        assertTimeTableRow(Iterables.getLast(updatedTrain.timeTableRows), null, 10);
    }


    @Test
    @Transactional
    public void liveEstimateVsOldForecastShouldEqualLiveEstimate() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        final TimeTableRow lastTimeTableRow = Iterables.getLast(train.timeTableRows);
        lastTimeTableRow.liveEstimateTime = lastTimeTableRow.scheduledTime.plusMinutes(5);
        lastTimeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;

        Forecast forecast = forecastFactory.create(lastTimeTableRow, -1);
        forecast.forecastTime = dp.nowInHelsinki().minusMinutes(50);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        final TimeTableRow last = Iterables.getLast(updatedTrain.timeTableRows);
        Assert.assertEquals(TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC, last.estimateSource);
        assertTimeTableRow(last, null, 5);
    }

    @Test
    @Transactional
    public void actualTimeVsForecastShouldEqualActualTime() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        final TimeTableRow lastTimeTableRow = Iterables.getLast(train.timeTableRows);
        lastTimeTableRow.actualTime = lastTimeTableRow.scheduledTime.plusMinutes(5);

        Forecast forecast = forecastFactory.create(Iterables.getLast(train.timeTableRows), 10);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        assertTimeTableRow(Iterables.getLast(updatedTrain.timeTableRows), 5, null);
    }

    @Test
    @Transactional
    public void actualVsForecast() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        train.timeTableRows.get(0).actualTime = train.timeTableRows.get(0).scheduledTime.plusMinutes(3);

        train.timeTableRows.get(1).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        train.timeTableRows.get(1).liveEstimateTime = train.timeTableRows.get(1).scheduledTime.plusMinutes(4);

        train.timeTableRows.get(2).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        train.timeTableRows.get(2).liveEstimateTime = train.timeTableRows.get(2).scheduledTime.plusMinutes(4);

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(1), 5);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        assertTimeTableRow(updatedTrain.timeTableRows.get(0), 3, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(1), null, 5);
        assertTimeTableRow(updatedTrain.timeTableRows.get(2), null, 4);
        assertTimeTableRow(updatedTrain.timeTableRows.get(3), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(4), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(5), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(7), null, null);
    }

    @Test
    @Transactional
    public void actualVsForecast2() {
        final Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        train.timeTableRows.get(5).actualTime = train.timeTableRows.get(5).scheduledTime.plusMinutes(3);

        train.timeTableRows.get(6).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        train.timeTableRows.get(6).liveEstimateTime = train.timeTableRows.get(6).scheduledTime.plusMinutes(4);

        train.timeTableRows.get(7).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        train.timeTableRows.get(7).liveEstimateTime = train.timeTableRows.get(7).scheduledTime.plusMinutes(4);

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(1), 5);
        Forecast earlyForecast = forecastFactory.create(train.timeTableRows.get(4), 5);

        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast, earlyForecast));

        assertTimeTableRow(updatedTrain.timeTableRows.get(0), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(1), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(2), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(3), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(4), null, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(5), 3, null);
        assertTimeTableRow(updatedTrain.timeTableRows.get(6), null, 4);
        assertTimeTableRow(updatedTrain.timeTableRows.get(7), null, 4);
    }

    @Test
    @Transactional
    public void earlyManualMikuVsLaterManualLiikeEstimateShouldNotClearLiike() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        }
        train.timeTableRows.get(0).estimateSource = TimeTableRow.EstimateSourceEnum.MIKU_USER;

        train.timeTableRows.get(5).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_USER;
        train.timeTableRows.get(6).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        train.timeTableRows.get(7).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(0), 11);

        train.version = -1L;
        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        final List<TimeTableRow> updatedRows = updatedTrain.timeTableRows;
        Assert.assertEquals(updatedRows.get(0).estimateSource, TimeTableRow.EstimateSourceEnum.MIKU_USER);
        Assert.assertEquals(updatedRows.get(1).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(2).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(3).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(4).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(5).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_USER);
        Assert.assertEquals(updatedRows.get(6).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(7).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
    }

    @Test
    @Transactional
    public void earlyManualMikuVsLaterManualLiikeEstimateShouldNotClearLiikeV2() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        }
        train.timeTableRows.get(0).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_USER;

        train.timeTableRows.get(5).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_USER;
        train.timeTableRows.get(6).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        train.timeTableRows.get(7).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(0), 11);

        train.version = -1L;
        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        final List<TimeTableRow> updatedRows = updatedTrain.timeTableRows;
        Assert.assertEquals(updatedRows.get(0).estimateSource, TimeTableRow.EstimateSourceEnum.MIKU_USER);
        Assert.assertEquals(updatedRows.get(1).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(2).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(3).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(4).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(5).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_USER);
        Assert.assertEquals(updatedRows.get(6).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
        Assert.assertEquals(updatedRows.get(7).estimateSource, TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC);
    }

    @Test
    @Transactional
    public void manualVsManulShouldProduceLatestManual2() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        }
        train.timeTableRows.get(0).estimateSource = TimeTableRow.EstimateSourceEnum.LIIKE_USER;

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(0), 11);

        train.version = -1L;
        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        assertTimeTableRow(updatedTrain.timeTableRows.get(0), null, 11);
    }

    @Test
    @Transactional
    public void unknownDelayAndThenExternalShouldResultInExternal() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);

        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.COMBOCALC;
        }

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(0), 11);
        forecast.forecastTime = null;
        forecast.difference = null;

        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        assertTimeTableRow(updatedTrain.timeTableRows.get(0), null, null);
        Assert.assertTrue(updatedTrain.timeTableRows.get(0).unknownDelay);

        Forecast forecastLater = forecastFactory.create(updatedTrain.timeTableRows.get(0), 1);

        Train updatedTrainLater = forecastMergingService.mergeEstimates(updatedTrain, Arrays.asList(forecastLater));

        assertTimeTableRow(updatedTrainLater.timeTableRows.get(0), null, 1);
        Assert.assertNull(updatedTrainLater.timeTableRows.get(0).unknownDelay);

    }

    @Test
    @Transactional
    public void unknownDelayForFirstTimeTableRowShouldWork() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.COMBOCALC;
        }

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(0), 11);
        forecast.forecastTime = null;
        forecast.difference = null;

        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        Assert.assertEquals(true, updatedTrain.timeTableRows.get(0).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(1).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(2).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(3).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(4).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(5).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(6).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(7).unknownDelay);
    }

    @Test
    @Transactional
    public void unknownDelayForMiddleTimeTableRowShouldWork() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.COMBOCALC;
        }

        Forecast forecast = forecastFactory.create(train.timeTableRows.get(2), 11);
        forecast.forecastTime = null;
        forecast.difference = null;

        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        Assert.assertEquals(null, updatedTrain.timeTableRows.get(0).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(1).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(2).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(3).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(4).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(5).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(6).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(7).unknownDelay);
    }

    @Test
    @Transactional
    public void unknownDelayForLastTimeTableRowShouldWork() {
        Train train = trainFactory.createBaseTrain();

        clearActualTimesAndEstimates(train);


        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.liveEstimateTime = timeTableRow.scheduledTime.plusMinutes(1);
            timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.COMBOCALC;
        }

        Forecast forecast = forecastFactory.create(Iterables.getLast(train.timeTableRows), 11);
        forecast.forecastTime = null;
        forecast.difference = null;

        train = trainRepository.save(train);
        train.timeTableRows = timeTableRowRepository.saveAll(train.timeTableRows);

        Train updatedTrain = forecastMergingService.mergeEstimates(train, Arrays.asList(forecast));

        Assert.assertEquals(null, updatedTrain.timeTableRows.get(0).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(1).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(2).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(3).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(4).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(5).unknownDelay);
        Assert.assertEquals(null, updatedTrain.timeTableRows.get(6).unknownDelay);
        Assert.assertEquals(true, updatedTrain.timeTableRows.get(7).unknownDelay);
    }

    private void clearActualTimesAndEstimates(final Train train) {
        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.actualTime = null;
            timeTableRow.liveEstimateTime = null;
        }
    }

    private void assertTimeTableRow(TimeTableRow timeTableRow, Integer actualTime, Integer liveEstimateTime) {
        if (actualTime == null) {
            Assert.assertEquals(null, timeTableRow.actualTime);
        } else {
            Assert.assertEquals(timeTableRow.scheduledTime.plusMinutes(actualTime), timeTableRow.actualTime);
        }

        if (liveEstimateTime == null) {
            Assert.assertEquals(null, timeTableRow.liveEstimateTime);
        } else {
            Assert.assertEquals(timeTableRow.scheduledTime.plusMinutes(liveEstimateTime), timeTableRow.liveEstimateTime);
        }
    }
}
