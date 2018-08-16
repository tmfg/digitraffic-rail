package fi.livi.rata.avoindata.updater.service.miku;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

@Service
public class ForecastMergingService {
    public static final int OLD_FORECAST_MINUTE_LIMIT = 3;
    private Logger log = LoggerFactory.getLogger(ForecastMergingService.class);

    private Set<String> allowedSources = Sets.newHashSet("COMBOCALC", "MIKUUSER", "LIIKEUSER");

    @Autowired
    private TrainPersistService trainPersistService;
    @Autowired
    private DateProvider dp;

    public Train mergeEstimates(final Train train, final List<Forecast> forecasts) {
        List<Forecast> forecastsFilteredById = filterOutDoubleForecasts(forecasts);

        Map<TimeTableRowId, Forecast> forecastMap = Maps.uniqueIndex(Iterables.filter(forecastsFilteredById, f -> allowedSources.contains(f.source)), f -> f.timeTableRow.id);

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
                } else if (forecast.forecastTime != null && lastActualTimetableRowIndex != -1 && forecast.forecastTime.isBefore(
                        train.timeTableRows.get(lastActualTimetableRowIndex).actualTime)) {
//                    log.debug("Not using {} on {} because later actual time found: {}", forecast, timeTableRow,
//                            train.timeTableRows.get(lastActualTimetableRowIndex).actualTime);
                    continue;
                    // Old (impossible) forecasts are not used
                } else if (forecast.forecastTime != null && forecast.forecastTime.isBefore(nowAndSome)) {
//                    log.debug("Not using {} on {} because it is old.", forecast, timeTableRow);
                    continue;
                } else {
                    createExternalForecast(train, newMaxVersion, timeTableRow, forecast);
                }
            }

        }

        return train;
    }

    private List<Forecast> filterOutDoubleForecasts(List<Forecast> forecasts) {
        Ordering<Forecast> ordering = Ordering.from((Comparator<Forecast>) (o1, o2) -> o1.id.compareTo(o2.id));

        ImmutableListMultimap<TimeTableRowId, Forecast> forecastsById = Multimaps.index(forecasts, f -> f.timeTableRow.id);

        List<Forecast> forecastsFilteredById = new ArrayList<>();
        for (TimeTableRowId id : forecastsById.keySet()) {
            ImmutableList<Forecast> forecastList = forecastsById.get(id);
            if (forecastList.size() == 1) {
                forecastsFilteredById.add(forecastList.get(0));
            } else {
                forecastsFilteredById.add(ordering.sortedCopy(forecastList).get(0));
            }
        }
        return forecastsFilteredById;
    }

    private void createExternalForecast(Train train, long newMaxVersion, TimeTableRow timeTableRow, Forecast forecast) {
        if (forecast.forecastTime == null) {
            log.info("Merged unknownDelay forecast {}", forecast);
            timeTableRow.unknownDelay = true;
            timeTableRow.liveEstimateTime = null;
        } else {
            final Duration duration = Duration.between(timeTableRow.scheduledTime, forecast.forecastTime);
            timeTableRow.differenceInMinutes = duration.toMinutes();

            timeTableRow.unknownDelay = null;
            timeTableRow.liveEstimateTime = forecast.forecastTime;
        }
        timeTableRow.estimateSource = convertForecastSource(forecast.source);
        if (train.version < newMaxVersion) {
            train.version = newMaxVersion;
        }
    }

    private TimeTableRow.EstimateSourceEnum convertForecastSource(String source) {
        if (source.equals("LIIKEUSER")) {
            return TimeTableRow.EstimateSourceEnum.LIIKE_USER;
        } else if (source.equals("MIKUUSER")) {
            return TimeTableRow.EstimateSourceEnum.MIKU_USER;
        } else if (source.equals("COMBOCALC")) {
            return TimeTableRow.EstimateSourceEnum.COMBOCALC;
        } else if (source.equals("LIIKE_AUTOMATIC")) {
            return TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC;
        } else {
            log.error("Could not parse Enum for forecast source " + source);
            return TimeTableRow.EstimateSourceEnum.UNKNOWN;
        }
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
}
