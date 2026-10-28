package fi.livi.rata.avoindata.updater.service.isuptodate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import fi.livi.rata.avoindata.common.dao.LastUpdateRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.common.LastUpdate;
import fi.livi.rata.avoindata.updater.service.SimpleTransactionManager;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final WebClient webClient;
    private final SimpleTransactionManager simpleTransactionManager;
    private final GeneratedExportRepository generatedExportRepository;
    private final LastUpdateRepository lastUpdateRepository;

    @Value("${updater.siri.et.enabled:false}")
    private boolean siriEtEnabled;
    @Value("${updater.siri.vm.enabled:false}")
    private boolean siriVmEnabled;
    @Value("${updater.siri.sx.enabled:false}")
    private boolean siriSxEnabled;

    public LastUpdateService(final WebClient webClient, SimpleTransactionManager simpleTransactionManager, GeneratedExportRepository generatedExportRepository,
                             LastUpdateRepository lastUpdateRepository) {
        this.webClient = webClient;
        this.simpleTransactionManager = simpleTransactionManager;
        this.generatedExportRepository = generatedExportRepository;
        this.lastUpdateRepository = lastUpdateRepository;
    }

    public enum LastUpdatedType {
        TRAINS,
        COMPOSITIONS,
        ROUTESETS,
        TRAIN_RUNNING_MESSAGES,
        TRAIN_LOCATIONS,
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
        TRAIN_LOCATIONS_DUMP,
        TRAINS_DUMP,
        COMPOSITIONS_DUMP,
        SIRI_ET,
        SIRI_VM,
        SIRI_SX_STATIONS,
        SIRI_SX_TRAINS
    }

    /** Updated rarely, so the time is stored in the database to survive a restart. */
    private static final Set<LastUpdatedType> STORED_IN_DATABASE = Set.of(
            LastUpdatedType.LOCALIZATIONS,
            LastUpdatedType.OPERATORS,
            LastUpdatedType.TIME_TABLE_PERIODS,
            LastUpdatedType.TRAIN_RUNNING_MESSAGE_RULES,
            LastUpdatedType.TRACKSECTIONS,
            LastUpdatedType.STATIONS,
            LastUpdatedType.CATEGORY_CODES,
            LastUpdatedType.FUTURE_TRAINS,
            LastUpdatedType.OLD_TRAINS);

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

        prefixToEnumMap.put("localizations", LastUpdatedType.LOCALIZATIONS);
        prefixToEnumMap.put("operators", LastUpdatedType.OPERATORS);
        prefixToEnumMap.put("timetableperiods", LastUpdatedType.TIME_TABLE_PERIODS);
        prefixToEnumMap.put("train-running-message-rules", LastUpdatedType.TRAIN_RUNNING_MESSAGE_RULES);
        prefixToEnumMap.put("tracksections", LastUpdatedType.TRACKSECTIONS);
        prefixToEnumMap.put("stations", LastUpdatedType.STATIONS);
        prefixToEnumMap.put("categorycodes", LastUpdatedType.CATEGORY_CODES);

        initializeLastUpdateTimes();
    }

    /**
     * Every data type is listed from the start, so a type that never gets updated shows up as outdated.
     * The time is read from the database when the data is stored there, otherwise it is the start time.
     */
    private void initializeLastUpdateTimes() {
        final Instant started = Instant.now();
        for (final LastUpdatedType type : LastUpdatedType.values()) {
            if (isInUse(type)) {
                lastUpdateTimes.put(type, started);
            }
        }

        simpleTransactionManager.executeInTransaction(() -> {
            lastUpdateRepository.findAll().forEach(this::initializeFromStored);
            initializeFromExport(LastUpdatedType.GTFS, "gtfs-passenger-stops.zip");
            if (siriEtEnabled) {
                initializeFromExport(LastUpdatedType.SIRI_ET, "siri-et.xml");
            }
            if (siriVmEnabled) {
                initializeFromExport(LastUpdatedType.SIRI_VM, "siri-vm.xml");
            }
            if (siriSxEnabled) {
                initializeFromExport(LastUpdatedType.SIRI_SX_STATIONS, "siri-sx.xml");
            }
        });
    }

    private boolean isInUse(final LastUpdatedType type) {
        return switch (type) {
            case SIRI_ET -> siriEtEnabled;
            case SIRI_VM -> siriVmEnabled;
            case SIRI_SX_STATIONS, SIRI_SX_TRAINS -> siriSxEnabled;
            default -> true;
        };
    }

    private void initializeFromStored(final LastUpdate stored) {
        try {
            final LastUpdatedType type = LastUpdatedType.valueOf(stored.type);
            if (STORED_IN_DATABASE.contains(type)) {
                lastUpdateTimes.put(type, stored.lastUpdated);
            }
        } catch (final IllegalArgumentException e) {
            log.warn("Unknown last update type {} in database", stored.type);
        }
    }

    private void initializeFromExport(final LastUpdatedType type, final String fileName) {
        final var createdOpt = generatedExportRepository.findLatestCreatedByFileName(fileName);

        if (createdOpt.isPresent()) {
            lastUpdateTimes.put(type, createdOpt.get());
        } else {
            log.warn("No export {} found in database", fileName);
        }
    }

    /**
     * Task for updating update times in-memory for data types requiring an HTTP request
     * to get last update time (such as dumps in S3).
     */
    @Scheduled(fixedDelay = 300000)
    public void updateLastUpdateTimesScheduled() {
        final StopWatch stopWatch = StopWatch.createStarted();

        updateTrainLocationsDumpLastUpdated();
        updateMonthlyDumpLastUpdated(LastUpdatedType.TRAINS_DUMP, "trains");
        updateMonthlyDumpLastUpdated(LastUpdatedType.COMPOSITIONS_DUMP, "compositions");

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
        final Instant now = Instant.now();
        lastUpdateTimes.put(lastUpdatedType, now);

        if (STORED_IN_DATABASE.contains(lastUpdatedType)) {
            try {
                simpleTransactionManager.executeInTransaction(() -> {
                    lastUpdateRepository.save(new LastUpdate(lastUpdatedType.name(), now));
                });
            } catch (final Exception e) {
                log.error("Could not store last update time of {}", lastUpdatedType, e);
            }
        }
    }

    private void updateTrainLocationsDumpLastUpdated() {
        final Instant lastUpdated = getLastUpdatedForUrl(String.format(
                "https://rata.digitraffic.fi/api/v1/train-locations/dumps/digitraffic-rata-train-locations-%s.zip",
                DateProvider.dateInHelsinki().minusDays(3)));
        if (lastUpdated != null) {
            lastUpdateTimes.put(LastUpdatedType.TRAIN_LOCATIONS_DUMP, lastUpdated);
        }
    }

    /**
     * The dump of the previous month is made on the 5th, so until then the month before it is the latest.
     */
    private void updateMonthlyDumpLastUpdated(final LastUpdatedType type, final String name) {
        final LocalDate today = DateProvider.dateInHelsinki();
        final LocalDate month = today.withDayOfMonth(1).minusMonths(today.getDayOfMonth() > 5 ? 1 : 2);
        final Instant lastUpdated = getLastUpdatedForUrl(String.format(
                "https://rata.digitraffic.fi/api/v1/%s/dumps/digitraffic-rata-%s-%s.zip", name, name, month));
        if (lastUpdated != null) {
            lastUpdateTimes.put(type, lastUpdated);
        }
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
