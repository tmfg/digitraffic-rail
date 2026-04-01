package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.stopmonitoring.UnknownDelayOrTrackUpdaterService;
import fi.livi.rata.avoindata.updater.service.stopsector.StopSectorUpdater;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.TrainPublishingService;
import fi.livi.rata.avoindata.updater.service.routeset.TimeTableRowByRoutesetUpdateService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;
import jakarta.annotation.PostConstruct;

@Service
public class TrainInitializerService extends AbstractDatabaseInitializer<Train> {
    private final TrainPersistService trainPersistService;
    private final TrainLockExecutor trainLockExecutor;
    private final TrainPublishingService trainPublishingService;
    private final TimeTableRowByRoutesetUpdateService timeTableRowByRoutesetUpdateService;
    private final StopSectorUpdater stopSectorUpdater;
    private final UnknownDelayOrTrackUpdaterService unknownDelayOrTrackUpdaterService;
    private final LastUpdateService lastUpdateService;

    private static final Logger log = LoggerFactory.getLogger(TrainInitializerService.class);

    /**
     * Source version (fira-data-version) used for querying the /trains API.
     * Initialized from DB max source_version on startup, then updated from the
     * fira-data-version response header after each successful query.
     */
    private final AtomicLong currentSourceVersion = new AtomicLong(-1L);

    public TrainInitializerService(final TrainPersistService trainPersistService,
                                   final TrainLockExecutor trainLockExecutor,
                                   final TrainPublishingService trainPublishingService,
                                   final TimeTableRowByRoutesetUpdateService timeTableRowByRoutesetUpdateService,
                                   final StopSectorUpdater stopSectorUpdater,
                                   final UnknownDelayOrTrackUpdaterService unknownDelayOrTrackUpdaterService,
                                   final LastUpdateService lastUpdateService) {
        this.trainPersistService = trainPersistService;
        this.trainLockExecutor = trainLockExecutor;
        this.trainPublishingService = trainPublishingService;
        this.timeTableRowByRoutesetUpdateService = timeTableRowByRoutesetUpdateService;
        this.stopSectorUpdater = stopSectorUpdater;
        this.unknownDelayOrTrackUpdaterService = unknownDelayOrTrackUpdaterService;
        this.lastUpdateService = lastUpdateService;
    }

    @PostConstruct
    private void initializeSourceVersion() {
        final long dbMaxSourceVersion = trainPersistService.getMaxSourceVersion();
        if (dbMaxSourceVersion > 0) {
            currentSourceVersion.set(dbMaxSourceVersion);
            log.info("method=initializeSourceVersion Initialized currentSourceVersion={} from database source_version", currentSourceVersion.get());
        }
        // this path is relevant only when the application is being started in a situation where source_version hasn't yet been set for any Trains and the API version hasn't drifted from the source version
        else {
            final long dbMaxApiVersion = trainPersistService.getMaxApiVersion();
            currentSourceVersion.set(dbMaxApiVersion);
            log.info("method=initializeSourceVersion No source_version found in database, falling back to max API version currentSourceVersion={}", currentSourceVersion.get());
        }
    }

    @Override
    public String getPrefix() {
        return "trains";
    }

    @Override
    public AbstractPersistService<Train> getPersistService() {
        return trainPersistService;
    }

    @Override
    protected Class<Train[]> getEntityCollectionClass() {
        return Train[].class;
    }

    @Override
    protected List<Train> doUpdate() {
        final StopWatch time = StopWatch.createStarted();

        try {
            final long querySourceVersion = currentSourceVersion.get();
            final var response = fetchData(querySourceVersion);
            final List<Train> trains = Arrays.asList(response.body());
            final long middle = time.getDuration().toMillis();

            final var updatedTrains = trainLockExecutor.executeInLock(this.getPrefix(), () -> {
                final Long dbMaxApiVersion = trainPersistService.getMaxApiVersion();

                modifyEntitiesBeforePersist(trains);

                final var updatedEntities = persistTrains(trains, response.version(), querySourceVersion);

                // log both dbMaxApiVersion and the source version from the fira-data-version header for debugging
                logUpdate(querySourceVersion, time.getDuration().toMillis(), updatedEntities.size(),
                        currentSourceVersion.get(), dbMaxApiVersion, getPrefix(), middle);

                return updatedEntities;
            });

           trainPublishingService.publish(updatedTrains);

           return updatedTrains;
        } catch (final Throwable t) {
            log.error("method=doUpdate failed {} tookMs={}", t.getMessage(), time.getDuration().toMillis(), t);
            throw new RuntimeException(t);
        }
    }

    private RipaService.ResponseWithVersion<Train[]> fetchData(final long querySourceVersion) {
        log.debug("method=fetchData Starting data update");

        final String targetPath = String.format("%s?version=%d", getPrefix(), querySourceVersion);

        log.info("method=fetchData Fetching prefix={} from api={}", getPrefix(), targetPath);

        return ripaService.getFromRipaRestTemplateWithVersion(targetPath, Train[].class);
    }

    /**
     * Persists trains and advances the source version cursor.
     * If the fira-data-version response header is missing, trains are still persisted
     * but sourceVersion and currentSourceVersion are left unchanged so the next poll
     * retries from the same position and sourceVersion is never corrupted with an API version number.
     */
    private List<Train> persistTrains(final List<Train> trains, final Long responseSourceVersion, final long querySourceVersion) {
        final long previousSourceVersion = currentSourceVersion.get();

        if (responseSourceVersion == null) {
            log.error("method=persistTrains fira-data-version header not present in response, retaining currentSourceVersion={}", previousSourceVersion);
            return trainPersistService.updateEntities(trains);
        }

        log.info("method=persistTrains Updated currentSourceVersion from {} to {} (from fira-data-version header)",
                previousSourceVersion, responseSourceVersion);

        trains.forEach(t -> t.sourceVersion = responseSourceVersion);

        final List<Train> updatedEntities = trainPersistService.updateEntities(trains);

        currentSourceVersion.set(responseSourceVersion);

        lastUpdateService.update(getPrefix());

        return updatedEntities;
    }

    private void logUpdate(final long previousSourceVersion, final long tookMs, final long count, final long newSourceVersion, final long dbMaxApiVersion, final String prefix,
                           final long middleMs) {
        log.info("method=logUpdate Updated data for count={} prefix={} in tookMs={} ms total ( json retrieve {} ms, previousSourceVersion={} newSourceVersion={} sourceVersionDiff={} dbMaxApiVersion={} )",
            count, prefix, tookMs, middleMs, previousSourceVersion, newSourceVersion, (newSourceVersion - previousSourceVersion), dbMaxApiVersion);
    }

    @Override
    public List<Train> modifyEntitiesBeforePersist(final List<Train> trains) {
        if (!trains.isEmpty()) {
            mergeUdots(trains);
            mergeStopSectors(trains);
            mergeRoutesets(trains);
        }

        return trains;
    }

    private void mergeUdots(final List<Train> trains) {
        TimingUtil.log(log, "mergeUdots", () -> unknownDelayOrTrackUpdaterService.mergeUdots(trains));
    }

    private void mergeStopSectors(final List<Train> trains) {
        TimingUtil.log(log, "mergeStopSectors", () -> stopSectorUpdater.mergeStopSectors(trains));
    }

    private void mergeRoutesets(final List<Train> trains) {
        TimingUtil.log(log, "mergeRoutesets", () -> timeTableRowByRoutesetUpdateService.updateByTrains(trains));
    }
}
