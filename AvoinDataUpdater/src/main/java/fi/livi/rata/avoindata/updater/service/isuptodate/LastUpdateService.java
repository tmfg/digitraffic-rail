package fi.livi.rata.avoindata.updater.service.isuptodate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.digitraffic.common.util.TimeUtil;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import jakarta.annotation.PostConstruct;

@Service
public class LastUpdateService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WebClient webClient;

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
        prefixToEnumMap.put("categorycodes", LastUpdatedType.CATEGORY_CODES);
    }

    /**
     * Task for updating update times in-memory for data types requiring an HTTP request
     * to get last update time (such as dumps in S3).
     */
    @Scheduled(fixedDelay = 300000)
    public void updateLastUpdateTimesScheduled() {
        final StopWatch stopWatch = StopWatch.createStarted();

        final Instant trainLocationsDumpLastUpdated = getLastUpdatedForUrl(String.format("https://rata.digitraffic.fi/api/v1/train-locations/dumps/digitraffic-rata-train-locations-%s.zip",
                DateProvider.dateInHelsinki().minusDays(3)));
        if (trainLocationsDumpLastUpdated != null) {
            lastUpdateTimes.put(LastUpdatedType.TRAIN_LOCATIONS_DUMP, trainLocationsDumpLastUpdated);
        }
        stopWatch.stop();

        log.info("method=updateLastUpdateTimesScheduled tookMs={} ", stopWatch.getTime());
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

    private Instant getLastUpdatedForUrl(final String url) {
        try {
            final HttpHeaders httpHeaders = Objects.requireNonNull(webClient.head().uri(url).retrieve().toBodilessEntity().block()).getHeaders();

            return TimeUtil.toInstant(httpHeaders.getLastModified());
        } catch (final Exception e) {
            log.error("method=getLastUpdatedForUrl Error getting last updated for url {}", url, e);
            return null;
        }
    }
    public Map<LastUpdatedType, Instant> getLastUpdateTimes() {
        return lastUpdateTimes;
    }
}
