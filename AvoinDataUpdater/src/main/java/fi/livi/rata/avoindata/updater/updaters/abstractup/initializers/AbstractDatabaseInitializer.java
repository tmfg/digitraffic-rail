package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.updater.ExceptionLoggingRunnable;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.InitializationPeriod;
import jakarta.annotation.PostConstruct;

@Service
public abstract class AbstractDatabaseInitializer<EntityType> implements DisposableBean {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final static Logger staticLogger = LoggerFactory.getLogger(AbstractDatabaseInitializer.class);
    private static final int NUMBER_OF_THREADS_TO_INITIALIZE_WITH = 1;

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    protected RipaService ripaService;

    @Autowired
    private LastUpdateService lastUpdateService;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    protected String prefix;
    private InitializationPeriod trainInitializationPeriod;
    private AbstractPersistService<EntityType> persistService;

    public abstract String getPrefix();

    public abstract AbstractPersistService<EntityType> getPersistService();

    public List<EntityType> modifyEntitiesBeforePersist(final List<EntityType> entities) {
        return entities;
    }

    private ScheduledThreadPoolExecutor scheduledExecutorService;

    @PostConstruct
    private void setup() {
        if (Strings.isNullOrEmpty(liikeInterfaceUrl)) {
            log.info("method=setup prefix={} updater.liikeinterface-url is null. Skipping initilization.", getPrefix());
            return;
        }

        this.prefix = getPrefix();
        this.persistService = getPersistService();
        this.trainInitializationPeriod = new InitializationPeriod(environment, "updater." + prefix);
        this.scheduledExecutorService = new ScheduledThreadPoolExecutor(1);
        scheduledExecutorService.setRemoveOnCancelPolicy(false);
    }

    @Override
    public void destroy() throws Exception {
        waitUntilTasksAreDoneAndShutDown(scheduledExecutorService, log);
    }

    public ExecutorService initializeInLockMode() {
        log.info("method=initializeInLockMode prefix={}", getPrefix());
        return addDataInitializeTasks(trainInitializationPeriod.lastDateInLockMode, trainInitializationPeriod.firstDateInLockMode);
    }

    public ExecutorService initializeInLazyMode() {
        log.info("method=initializeInLazyMode prefix={}", getPrefix());
        return addDataInitializeTasks(trainInitializationPeriod.firstDateInLockMode, trainInitializationPeriod.endNonLockDate);
    }

    public void startUpdating(final int delay) {
        try {
            log.info("method=startUpdating prefix={} updateRate={}", getPrefix(), delay);
            scheduleAtFixedRate(this::startUpdate, delay);
            log.info("method=startUpdating prefix={} updateRate={} done", getPrefix(), delay);
        } catch (final Exception e) {
            log.error("method=startUpdating prefix={} updateRate={} Failed {}", getPrefix(), delay, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void scheduleAtFixedRate(final Runnable runnable, final int updateRate) {
        log.info("method=scheduleAtFixedRate prefix={} updateRate={}", getPrefix(), updateRate);
        scheduledExecutorService.scheduleAtFixedRate(
                new ExceptionLoggingRunnable(runnable, getPrefix()), 1000, updateRate, TimeUnit.MILLISECONDS);
        log.info("method=scheduleAtFixedRate prefix={} updateRate={} done", getPrefix(), updateRate);
    }

    protected void startUpdate() {
        final StopWatch time = StopWatch.createStarted();
        try {
            log.debug("method=startUpdate prefix={} ", this.prefix);
            doUpdate();
            log.debug("method=startUpdate prefix={} done tookMs={}", this.prefix, time.getDuration().toMillis());
        } catch (final Throwable t) {
            log.error("method=startUpdate prefix={} update failed tookMs={}", this.prefix, time.getDuration().toMillis(), t);
        }
    }

    protected List<EntityType> doUpdate() {
        final StopWatch time = StopWatch.createStarted();
        try {
            log.debug("method=doUpdate prefix={} Starting data update", this.prefix);

            final Long latestVersion = persistService.getMaxVersion();

            List<EntityType> objects = getObjectsNewerThanVersion(this.prefix, this.getEntityCollectionClass(), latestVersion);

            final long middle = time.getDuration().toMillis();

            objects = modifyEntitiesBeforePersist(objects);

            final List<EntityType> updatedEntities = persistService.updateEntities(objects);

            logUpdate(latestVersion, time.getDuration().toMillis(), updatedEntities.size(), persistService.getMaxVersion(), this.prefix, middle, updatedEntities);

            lastUpdateService.update(this.prefix);

            return updatedEntities;
        } catch (final Throwable t) {
            log.error("method=doUpdate prefix={} update failed {} tookMs={}", this.prefix, t.getMessage(), time.getDuration().toMillis(), t);
            throw new RuntimeException(t);
        }
    }

    protected void logUpdate(final long latestVersion, final long tookMs, final long count, final long newVersion, final String prefix,
                             final long middleMs, final List<EntityType> objects) {
        log.info("method=logUpdate Updated data for count={} prefix={} in tookMs={} ms total ( json retrieve {} ms, oldVersion={} newVersion={} versionDiff={} )", count, prefix,
                tookMs, middleMs, latestVersion, newVersion,
                (newVersion - latestVersion));
    }

    public void clearEntities() {
        persistService.clearEntities();
    }

    public static void waitUntilTasksAreDoneAndShutDown(final ExecutorService executorService, final Logger log) throws InterruptedException {
        executorService.shutdown(); // Previously submitted tasks are executed, but no new tasks will be accepted
        if (!executorService.awaitTermination(20, TimeUnit.MINUTES)) {
            log.error("method=waitUntilTasksAreDone Tasks termination didn't succeed in given 20 min time limit");
        }
        executorService.shutdownNow();
    }

    public ExecutorService addDataInitializeTasks(final LocalDate startDate, final LocalDate endDate) {
        final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS_TO_INITIALIZE_WITH);

        log.info("method=addDataInitializeTasks Adding initialization tasks for prefix={} from={} to={} ", getPrefix(), startDate, endDate);

        for (LocalDate i = startDate; i.isAfter(endDate); i = i.minusDays(1)) {
            final LocalDate currentDate = i;
            executorService.execute(() -> getAndSaveForADate(currentDate));
        }

        return executorService;
    }

    public void getAndSaveForADate(final LocalDate date) {
        final StopWatch now = StopWatch.createStarted();
        final List<EntityType> entities = getForADay(this.prefix, date, getEntityCollectionClass());
        this.persistService.addEntities(entities);
        log.debug(String.format("method=getAndSaveForADate Initialized %s data for %s (%d %s) in %s ms", this.prefix, date, entities.size(), this.prefix, now.getDuration().toMillis()));
    }

    protected abstract Class<EntityType[]> getEntityCollectionClass();

    protected List<EntityType> getObjectsNewerThanVersion(final String path, final Class<EntityType[]> responseType, final long latestVersion) {
        final String targetPath = String.format("%s?version=%d", path, latestVersion);

        log.info("method=getObjectsNewerThanVersion Fetching prefix={} from api={}", this.prefix, targetPath);

        return Arrays.asList(ripaService.getFromRipaRestTemplate(targetPath, responseType));
    }

    protected List<EntityType> getForADay(final String path, final LocalDate date, final Class<EntityType[]> type) {
        final String targetUrl = StringUtil.format("{}?date={}", path, date);
        final EntityType[] results = getForObjectWithRetry(targetUrl, type);
        log.debug(StringUtil.format("method=getForADay api={} retrieved. Saving to database!", targetUrl));
        return Lists.newArrayList(results);
    }

    protected <Type> Type[] getForObjectWithRetry(final String path, final Class<Type[]> responseType) {
        final StopWatch startRetry = StopWatch.createStarted();
        final AtomicInteger retryCount = new AtomicInteger();
        try {
            return retryTemplate.execute(context -> {
                retryCount.addAndGet(1);
                log.info("method=getForObjectWithRetry Requesting data from {}", path);
                final StopWatch start = StopWatch.createStarted();
                try {
                    return ripaService.getFromRipa(path, responseType);
                } catch (final Exception e) {
                    log.error("method=getForObjectWithRetry Requesting data from api={} failed after {} s on retry attempt {} inside retry", path, start.getDuration().toMillis() / 1000, retryCount.get(), e);
                    throw e;
                }
            });
        } catch (final Exception e) {
            log.error("method=getForObjectWithRetry Requesting data in retry from api={} failed after {} s and {} retries", path, startRetry.getDuration().toMillis() / 1000, retryCount.get(), e);
            throw new RuntimeException(e);
        }
    }
}
