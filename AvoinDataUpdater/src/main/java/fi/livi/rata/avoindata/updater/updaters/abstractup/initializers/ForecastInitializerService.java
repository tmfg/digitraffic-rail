package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import com.google.common.collect.*;
import fi.livi.rata.avoindata.common.dao.train.ForecastRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.miku.ForecastMergingService;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenForecastFilter;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.ForecastPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class ForecastInitializerService extends AbstractDatabaseInitializer<Forecast> {
    private Logger log = LoggerFactory.getLogger(ForecastInitializerService.class);

    @Autowired
    private ForecastPersistService forecastPersistService;

    @Autowired
    private ForecastMergingService forecastMergingService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Autowired
    private BatchExecutionService bes;

    @Autowired
    private ForecastRepository forecastRepository;

    @Autowired
    private RecentlySeenForecastFilter recentlySeenForecastFilter;

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Override
    public String getPrefix() {
        return "forecasts";
    }

    @Override
    public AbstractPersistService<Forecast> getPersistService() {
        return forecastPersistService;
    }

    @Override
    protected Class<Forecast[]> getEntityCollectionClass() {
        return Forecast[].class;
    }

    @Override
    protected List<Forecast> doUpdate() {
        return trainLockExecutor.executeInLock(() -> super.doUpdate());
    }

    @Override
    public List<Forecast> modifyEntitiesBeforePersist(final List<Forecast> entities) {
        final List<Forecast> forecasts = recentlySeenForecastFilter.filter(entities);

        mergeForecasts(forecasts);

        return forecasts;
    }

    private void mergeForecasts(final List<Forecast> deserializedForecasts) {
        if (!deserializedForecasts.isEmpty()) {
            List<Forecast> totalForecasts = getTotalForecasts(deserializedForecasts);

            final ImmutableListMultimap<TrainId, Forecast> trainMap = Multimaps.index(totalForecasts, forecast -> {
                TimeTableRowId timeTableRowId = forecast.timeTableRow.getIdDirect(forecast.timeTableRow);
                return new TrainId(timeTableRowId.trainNumber, timeTableRowId.departureDate);
            });

            final List<Train> trains = bes.transform(Lists.newArrayList(trainMap.keySet()), l -> trainRepository.findTrains(l));

            final Map<TrainId, Train> fetchedTrainMap = Maps.uniqueIndex(trains, s -> s.id);

            List<Train> savedTrains = new ArrayList<>(trainMap.size());
            for (final TrainId trainId : trainMap.keySet()) {
                final Train fetchedTrain = fetchedTrainMap.get(trainId);
                if (fetchedTrain != null) {
                    final Map<TimeTableRowId, TimeTableRow> rowMap = Maps.uniqueIndex(fetchedTrain.timeTableRows, s -> s.id);
                    final List<Forecast> trainsForecasts = trainMap.get(trainId);
                    for (final Forecast forecast : trainsForecasts) {
                        final TimeTableRow timeTableRow = rowMap.get(forecast.timeTableRow.getIdDirect(forecast.timeTableRow));
                        if (timeTableRow != null && timeTableRow.id != null && timeTableRow.id.attapId != null) {
                            forecast.timeTableRow = timeTableRow;
                        } else {
                            log.warn("Trying to update null timeTableRow for forecast {}", forecast);
                        }
                    }

                    savedTrains.add(forecastMergingService.mergeEstimates(fetchedTrain, trainsForecasts));
                }
            }

            trainRepository.saveAll(savedTrains);
            for (final Train savedTrain : savedTrains) {
                timeTableRowRepository.saveAll(savedTrain.timeTableRows);
            }
        }
    }

    private List<Forecast> getTotalForecasts(final List<Forecast> deserializedForecasts) {
        List<TrainId> affectedTrains = new ArrayList<>();
        for (final Forecast deserializedForecast : deserializedForecasts) {
            TimeTableRowId timeTableRowId = deserializedForecast.timeTableRow.getIdDirect(deserializedForecast.timeTableRow);
            affectedTrains.add(new TrainId(timeTableRowId.trainNumber, timeTableRowId.departureDate));
        }

        final List<Forecast> forecastsFromDatabase = bes.transform(affectedTrains, l -> forecastRepository.findByTrains(l));

        final HashSet<Long> deserializedIds = Sets.newHashSet(Iterables.transform(deserializedForecasts, f -> f.id));

        List<Forecast> totalForecasts = new ArrayList<>();
        totalForecasts.addAll(deserializedForecasts);
        for (final Forecast forecast : forecastsFromDatabase) {
            if (!deserializedIds.contains(forecast.id)) {
                totalForecasts.add(forecast);
                deserializedIds.add(forecast.id);
            }
        }
        return totalForecasts;
    }
}
