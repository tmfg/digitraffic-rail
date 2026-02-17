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
     * Version number used for querying /trains API. Initialized from DB max version on startup,
     * then updated from the fira-data-version response header after each successful query.
     */
    private final AtomicLong currentVersion = new AtomicLong(-1L);

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
    private void initializeVersion() {
        final Long dbMaxVersion = trainPersistService.getMaxVersion();
        currentVersion.set(dbMaxVersion != null ? dbMaxVersion : -1L);
        log.info("method=initializeVersion Initialized currentVersion={} from database", currentVersion.get());
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
            final long queryVersion = currentVersion.get();
            final var response = fetchData(queryVersion);
            final List<Train> trains = Arrays.asList(response.body());
            final long middle = time.getDuration().toMillis();

            final var updatedTrains = trainLockExecutor.executeInLock(this.getPrefix(), () -> {
                final Long dbMaxVersion = trainPersistService.getMaxVersion();

                modifyEntitiesBeforePersist(trains);

                final var updatedEntities = persistTrains(trains, dbMaxVersion, response.version(), queryVersion);

                // log both dbMaxVersion and the version from the fira-data-version header for any debugging purposes
                logUpdate(queryVersion, time.getDuration().toMillis(), updatedEntities.size(),
                        currentVersion.get(), dbMaxVersion, getPrefix(), middle);

                return updatedEntities;
            });

           trainPublishingService.publish(updatedTrains);

           return updatedTrains;
        } catch (final Throwable t) {
            log.error("method=doUpdate failed {} tookMs={}", t.getMessage(), time.getDuration().toMillis(), t);
            throw new RuntimeException(t);
        }
    }

    private RipaService.ResponseWithVersion<Train[]> fetchData(final long queryVersion) {
        log.debug("method=fetchData Starting data update");

        final String targetPath = String.format("%s?version=%d", getPrefix(), queryVersion);

        log.info("method=fetchData Fetching prefix={} from api={}", getPrefix(), targetPath);

        return ripaService.getFromRipaRestTemplateWithVersion(targetPath, Train[].class);
    }

    /**
     * Custom update logic that uses the fira-data-version response header for version tracking.
     */
    private List<Train> persistTrains(final List<Train> trains, final Long dbMaxVersion, final Long responseVersion, final long queryVersion) {
        final List<Train> updatedEntities = trainPersistService.updateEntities(trains);
        final long previousVersion = currentVersion.get();

        // Update currentVersion from the fira-data-version header if present
        if (responseVersion != null) {
            currentVersion.set(responseVersion);
            log.info("method=persistTrains Updated currentVersion from {} to {} (from fira-data-version header)",
                previousVersion, responseVersion);
        } else {
            currentVersion.set(dbMaxVersion);
            log.error("method=persistTrains fira-data-version header not present in response, updating with max value from db from {} to {}",
                    previousVersion, dbMaxVersion);
        }

        lastUpdateService.update(getPrefix());

        return updatedEntities;
    }

    private void logUpdate(final long latestVersion, final long tookMs, final long count, final long newVersion, final long dbMaxVersion, final String prefix,
                           final long middleMs) {
        log.info("method=logUpdate Updated data for count={} prefix={} in tookMs={} ms total ( json retrieve {} ms, oldVersion={} newVersion={} versionDiff={} dbMaxVersion={} )",
            count, prefix, tookMs, middleMs, latestVersion, newVersion, (newVersion - latestVersion), dbMaxVersion);
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
