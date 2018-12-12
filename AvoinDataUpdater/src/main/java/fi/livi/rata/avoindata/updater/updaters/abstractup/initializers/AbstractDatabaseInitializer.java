package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;


import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

import com.amazonaws.xray.AWSXRay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.updater.ExceptionLoggingRunnable;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.InitializationPeriod;

@Service
public abstract class AbstractDatabaseInitializer<EntityType> {
    private static final Logger log = LoggerFactory.getLogger(AbstractDatabaseInitializer.class);

    @Autowired
    private InitializerRetryTemplate retryTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    protected String prefix;
    private InitializationPeriod trainInitializationPeriod;
    private AbstractPersistService<EntityType> persistService;

    public abstract String getPrefix();

    public abstract AbstractPersistService<EntityType> getPersistService();

    public List<EntityType> modifyEntitiesBeforePersist(List<EntityType> entities) {
        return entities;
    }

    @PostConstruct
    private void setup() {
        if (Strings.isNullOrEmpty(liikeInterfaceUrl)) {
            log.info("updater.liikeinterface-url is null. Skipping initilization.");
            return;
        }

        retryTemplate.setLogger(log);

        this.prefix = getPrefix();
        this.persistService = getPersistService();
        this.trainInitializationPeriod = new InitializationPeriod(environment, "updater." + prefix);
    }


    public ExecutorService initializeInLockMode() {
        return addDataInitializeTasks(trainInitializationPeriod.lastDateInLockMode, trainInitializationPeriod.firstDateInLockMode);
    }

    public ExecutorService initializeInLazyMode() {
        return addDataInitializeTasks(trainInitializationPeriod.firstDateInLockMode, trainInitializationPeriod.endNonLockDate);
    }


    public void startUpdating(int delay) {
        scheduleAtFixedRate(() -> startUpdate(), delay);
    }


    private void scheduleAtFixedRate(final Runnable runnable, final int updateRate) {
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(new ExceptionLoggingRunnable(runnable), 1000, updateRate, TimeUnit.MILLISECONDS);
    }

    protected void startUpdate() {
        AWSXRay.createSegment(this.getClass().getSimpleName(), (subsegment) -> {
            doUpdate();
        });
    }

    protected List<EntityType> doUpdate() {
        return AWSXRay.createSubsegment("abstract_doUpdate", (subsegment) -> {
            final Long latestVersion = persistService.getMaxVersion();
            final ZonedDateTime start = ZonedDateTime.now();

            List<EntityType> objects = getObjectsNewerThanVersion(this.prefix, this.getEntityCollectionClass(), latestVersion);

            final ZonedDateTime middle = ZonedDateTime.now();

            objects = modifyEntitiesBeforePersist(objects);

            final List<EntityType> updatedEntities = persistService.updateEntities(objects);

            logUpdate(latestVersion, start, updatedEntities.size(), persistService.getMaxVersion(), this.prefix, middle, updatedEntities);

            return updatedEntities;
        });
    }

    protected void logUpdate(final long latestVersion, final ZonedDateTime start, final long length, final long newVersion, final String name, final ZonedDateTime middle, final List<EntityType> objects) {
        log.info("Updated data for {} {} in {} ms total (json retrieve {} ms) (old version {}, new version {}, diff versions {})", length, name, Duration.between(start, ZonedDateTime.now()).toMillis(), Duration.between(start, middle).toMillis(), latestVersion, newVersion, (newVersion - latestVersion));
    }

    public void clearEntities() {
        persistService.clearEntities();
    }

    public static void waitUntilTasksAreDone(ExecutorService executorService) throws InterruptedException {
        executorService.shutdown();

        executorService.awaitTermination(20, TimeUnit.MINUTES);
    }

    public ExecutorService addDataInitializeTasks(final LocalDate startDate, final LocalDate endDate) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        log.info("Adding initialization tasks from {} to {}", startDate, endDate);

        for (LocalDate i = startDate; i.isAfter(endDate); i = i.minusDays(1)) {
            final LocalDate currentDate = i;
            executorService.execute(() -> getAndSaveForADate(currentDate));
        }

        return executorService;
    }

    public void getAndSaveForADate(final LocalDate date) {
        final ZonedDateTime now = ZonedDateTime.now();
        final List<EntityType> entities = getForADay(this.prefix, date, getEntityCollectionClass());
        this.persistService.addEntities(entities);
        log.debug(String.format("Initialized data for %s (%d %s) in %s ms", date, entities.size(), this.prefix, Duration.between(now, ZonedDateTime.now()).toMillis()));
    }

    protected abstract <A> Class<A> getEntityCollectionClass();

    protected List<EntityType> getObjectsNewerThanVersion(final String path, final Class<EntityType[]> responseType, final long latestVersion) {
        final String targetUrl = String.format("%s/%s?version=%d", liikeInterfaceUrl, path, latestVersion);
        return Lists.newArrayList(restTemplate.getForObject(targetUrl, responseType));
    }

    protected List<EntityType> getForADay(final String path, final LocalDate date, final Class<EntityType[]> type) {
        final String targetUrl = String.format("%s/%s?date=%s", liikeInterfaceUrl, path, date);
        final EntityType[] results = getForObjectWithRetry(targetUrl, type);
        log.debug(String.format("%s retrieved. Saving to database!", path));
        return Lists.newArrayList(results);
    }

    private EntityType[] getForObjectWithRetry(final String targetUrl, final Class<EntityType[]> responseType) {
        return retryTemplate.execute(context -> {
            log.info("Requesting data from " + targetUrl);
            final EntityType[] objects = restTemplate.getForObject(targetUrl, responseType);
            return objects;
        });
    }
}
