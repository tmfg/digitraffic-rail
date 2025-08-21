package fi.livi.rata.avoindata.updater.service.isuptodate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class LastUpdateService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public enum LastUpdatedType {
        TRAINS,
        COMPOSITIONS,
        ROUTESETS,
        TRAIN_RUNNING_MESSAGES,
        TRAIN_LOCATIONS,
        FORECASTS,
        LOCALIZATIONS,
        OPERATORS,
        TIME_TABLE_PERIODS,
        TRAIN_RUNNING_MESSAGE_RULES,
        TRACKSECTIONS,
        GTFS,
        FUTURE_TRAINS,
        OLD_TRAINS,
        STATIONS,
        CATEGORY_CODES,
        TRACK_WORK_NOTIFICATIONS,
        TRAFFIC_RESTRICTION_NOTIFICATIONS,
        TRAINS_DUMP,
        COMPOSITIONS_DUMP,
        TRAIN_LOCATIONS_DUMP
    }

    private final Map<String, LastUpdatedType> prefixToEnumMap = new HashMap<>();
    private final Map<LastUpdatedType, Instant> lastUpdateTimes = new HashMap<>();

    @PostConstruct
    public void setup() {
        prefixToEnumMap.put("trains", LastUpdatedType.TRAINS);
        prefixToEnumMap.put("routesets", LastUpdatedType.ROUTESETS);
        prefixToEnumMap.put("compositions", LastUpdatedType.COMPOSITIONS); // old compositions
        prefixToEnumMap.put("julkisetkokoonpanot", LastUpdatedType.COMPOSITIONS); // new compositions
        prefixToEnumMap.put("trainrunningmessages", LastUpdatedType.TRAIN_RUNNING_MESSAGES);
        prefixToEnumMap.put("trainLocations", LastUpdatedType.TRAIN_LOCATIONS);
        prefixToEnumMap.put("forecasts", LastUpdatedType.FORECASTS);

        prefixToEnumMap.put("localizations", LastUpdatedType.LOCALIZATIONS);
        prefixToEnumMap.put("operators", LastUpdatedType.OPERATORS);
        prefixToEnumMap.put("timetableperiods", LastUpdatedType.TIME_TABLE_PERIODS);
        prefixToEnumMap.put("train-running-message-rules", LastUpdatedType.TRAIN_RUNNING_MESSAGE_RULES);
        prefixToEnumMap.put("tracksections", LastUpdatedType.TRACKSECTIONS);
        prefixToEnumMap.put("stations", LastUpdatedType.STATIONS);
        prefixToEnumMap.put("/v1/reason-categories/latest/v1/reason-codes/latest", LastUpdatedType.CATEGORY_CODES);
    }

    public void update(final String lastUpdatedType) {
        final LastUpdatedType lastUpdateTypeAsEnum = prefixToEnumMap.get(lastUpdatedType);
        if (lastUpdateTypeAsEnum != null) {
            update(lastUpdateTypeAsEnum);
        } else {
            log.warn("No enum for string {}", lastUpdatedType);
        }
    }

    public void update(final LastUpdatedType lastUpdatedType) {
        lastUpdateTimes.put(lastUpdatedType, Instant.now());
    }

    public Map<LastUpdatedType, Instant> getLastUpdateTimes() {
        return lastUpdateTimes;
    }
}
