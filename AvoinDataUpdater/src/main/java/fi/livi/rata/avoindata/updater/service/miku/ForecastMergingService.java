package fi.livi.rata.avoindata.updater.service.miku;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ForecastMergingService {
    public static final int OLD_FORECAST_MINUTE_LIMIT = 3;
    private Logger log = LoggerFactory.getLogger(ForecastMergingService.class);

    @Autowired
    private TrainPersistService trainPersistService;
    @Autowired
    private DateProvider dp;
    public Train mergeEstimates(final Train train, final List<Forecast> forecasts) {
        Map<TimeTableRowId, Forecast> forecastMap = Maps.uniqueIndex(Iterables.filter(forecasts, f -> f.source.equals("MIKUUSER")),
                f -> f.timeTableRow.id);

        final ZonedDateTime nowAndSome = dp.nowInHelsinki().minusMinutes(OLD_FORECAST_MINUTE_LIMIT);
        int lastActualTimetableRowIndex = getIndexOfLastTimeTableRowWithActualTime(train);

        final long newMaxVersion = trainPersistService.getMaxVersion() + 1;

        for (int i = 0; i < train.timeTableRows.size(); i++) {
            //Current or future row has an actual time
            if (lastActualTimetableRowIndex >= i) {
                continue;
            } else {
                final TimeTableRow timeTableRow = train.timeTableRows.get(i);
                final Forecast forecast = forecastMap.get(timeTableRow.id);

                //No Miku Forecasts for TimeTableRow
                if (forecast == null) {
                    continue;
                    // Do not use if later actual time is found
                } else if (lastActualTimetableRowIndex != -1 && forecast.forecastTime.isBefore(
                        train.timeTableRows.get(lastActualTimetableRowIndex).actualTime)) {
                    log.debug("Not using {} on {} because later actual time found: {}", forecast, timeTableRow,
                            train.timeTableRows.get(lastActualTimetableRowIndex).actualTime);
                    continue;
                    // Old (impossible) forecasts are not used
                } else if (forecast.forecastTime.isBefore(nowAndSome)) {
                    log.debug("Not using {} on {} because it is old.", forecast, timeTableRow);
                    continue;
                    //Liike vs Miku manual Forecast conflict. Newer forecast should win (this if-clause defines Liike win condition)
                } else if (isLiikeManualEstimate(timeTableRow) && train.version > forecast.version) {
                    log.debug("Both manual estimates exist. Choosing liike since it is later. TTR: {}, Forecast: {}, Versions: {} vs {}",
                            timeTableRow, forecast, train.version, forecast.version);
                    continue;
                    //Free to forecast using Miku
                } else {
                    createMikuForecast(train, newMaxVersion, i, timeTableRow, forecast);
                }
            }

        }

        return train;
    }

    private void createMikuForecast(Train train, long newMaxVersion, int i, TimeTableRow timeTableRow, Forecast forecast) {
        log.debug("Manual estimate from {} ({}) {} ({}) -> {} ({}). Train: {}, Version: {} -> {}",
                timeTableRow.station.stationShortCode, timeTableRow.type, timeTableRow.liveEstimateTime,
                timeTableRow.estimateSource, forecast.forecastTime, TimeTableRow.EstimateSourceEnum.MIKU_USER, train,
                train.version, newMaxVersion);

        final Duration duration = Duration.between(timeTableRow.scheduledTime, forecast.forecastTime);
        timeTableRow.differenceInMinutes = duration.toMinutes();

        timeTableRow.liveEstimateTime = forecast.forecastTime;
        timeTableRow.estimateSource = TimeTableRow.EstimateSourceEnum.MIKU_USER;

        //Do automatic forecasts for every row thereafter
        createAutomaticForecasts(i, train);

        train.version = newMaxVersion;
    }

    private int getIndexOfLastTimeTableRowWithActualTime(final Train train) {
        int lastActualTimetableRowIndex = -1;
        for (int i = 0; i < train.timeTableRows.size(); i++) {
            TimeTableRow timeTableRow = train.timeTableRows.get(i);
            if (timeTableRow.actualTime != null) {
                lastActualTimetableRowIndex = i;
            }
        }
        return lastActualTimetableRowIndex;
    }

    private boolean isLiikeManualEstimate(final TimeTableRow timeTableRow) {
        return timeTableRow.liveEstimateTime != null && timeTableRow.estimateSource == TimeTableRow.EstimateSourceEnum.LIIKE_USER;
    }

    private void createAutomaticForecasts(final int startIndex, final Train train) {
        final TimeTableRow timeTableRow = train.timeTableRows.get(startIndex);
        final Duration difference = Duration.between(timeTableRow.scheduledTime, timeTableRow.liveEstimateTime);

        for (int i = startIndex + 1; i < train.timeTableRows.size(); i++) {
            final TimeTableRow row = train.timeTableRows.get(i);
            if (row.estimateSource == TimeTableRow.EstimateSourceEnum.LIIKE_USER || row.actualTime != null) {
                break;
            }
            row.liveEstimateTime = row.scheduledTime.plus(difference);
            row.estimateSource = TimeTableRow.EstimateSourceEnum.DIGITRAFFIC_AUTOMATIC;
            row.differenceInMinutes = difference.toMinutes();
            //            log.info("Wrote AUTOMATIC estimate {} for {}, Train: {}", row.liveEstimateTime, row, train);
        }
    }
}
