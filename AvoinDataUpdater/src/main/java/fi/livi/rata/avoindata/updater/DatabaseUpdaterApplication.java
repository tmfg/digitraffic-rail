package fi.livi.rata.avoindata.updater;

import static fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.AbstractDatabaseInitializer.waitUntilTasksAreDoneAndShutDown;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestClientException;

import com.google.common.base.Strings;

import fi.livi.rata.avoindata.common.ESystemStateProperty;
import fi.livi.rata.avoindata.common.dao.CustomGeneralRepositoryImpl;
import fi.livi.rata.avoindata.common.service.SystemStatePropertyService;
import fi.livi.rata.avoindata.updater.service.CompositionService;
import fi.livi.rata.avoindata.updater.service.TrainRunningMessageService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.CompositionInitializerService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.RoutesetInitializerService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.TrainInitializerService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.TrainRunningMessageInitializerService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

@SpringBootApplication
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = {"fi.livi.rata.avoindata.updater", "fi.livi.rata.avoindata.common"})
@EntityScan(basePackages = "fi.livi.rata.avoindata.common.domain")
@EnableJpaRepositories(basePackages = "fi.livi.rata.avoindata.common.dao", repositoryBaseClass = CustomGeneralRepositoryImpl.class)
public class DatabaseUpdaterApplication {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUpdaterApplication.class);

    public static void main(final String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));

        final SpringApplication application = createApplication();

        application.run(args);
    }

    private static SpringApplication createApplication() {
        final SpringApplication application = new SpringApplication(DatabaseUpdaterApplication.class);
        final Properties properties = new Properties();

        properties.put("myHostname", getHostname());

        application.setDefaultProperties(properties);
        return application;
    }

    private static String getHostname() {
        try {
            final InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName();
        } catch (final UnknownHostException ex) {
            return "unknown";
        }
    }

    @Configuration
    public static class Runner implements CommandLineRunner {

        @Autowired
        private CompositionService compositionService;

        @Autowired
        private TrainRunningMessageService trainRunningMessageService;

        @Autowired
        private TrainPersistService trainPersistService;

        @Autowired
        private SystemStatePropertyService systemStatePropertyService;


        @Autowired
        private TrainInitializerService trainInitializerService;

        @Autowired
        private RoutesetInitializerService routesetInitializerService;

        @Autowired
        private TrainRunningMessageInitializerService trainRunningMessageInitializerService;

        @Autowired
        private CompositionInitializerService compositionInitializerService;

        @Value("${updater.updateTrainsIntervalMillis:5000}")
        private int UPDATE_TRAINS_DELAY;

        @Value("${updater.updateCompositionsIntervalMillis:10000}")
        private int UPDATE_COMPOSITIONS_DELAY;

        @Value("${updater.updateTrainRunningMessagesIntervalMillis:2000}")
        private int UPDATE_TRAINRUNNINGMESSAGES_DELAY;

        @Value("${updater.updateRoutesetsIntervalMillis:4000}")
        private int UPDATE_ROUTESETS_DELAY;

        @Value("${updater.liikeinterface-url}")
        protected String liikeInterfaceUrl;


        @Override
        public void run(final String... args) throws RestClientException {
            if (Strings.isNullOrEmpty(liikeInterfaceUrl)) {
                log.info("method=run updater.liikeinterface-url is null. Skipping initilization.");
                return;
            }

            startInitPhaseIfNeeded();

            startUpdating();
        }

        private void startInitPhaseIfNeeded() {
            try {
                if (isInitializationNeeded()) {
                    log.info("method=startInitPhaseIfNeeded Database needs to be initialized!");
                    clearDatabase();
                    initializeInLockMode();
                    startLazyUpdate();
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void startUpdating() {
            log.info("method=startUpdating Start updating in scheduled mode");
            trainInitializerService.startUpdating(UPDATE_TRAINS_DELAY);
            compositionInitializerService.startUpdating(UPDATE_COMPOSITIONS_DELAY);
            trainRunningMessageInitializerService.startUpdating(UPDATE_TRAINRUNNINGMESSAGES_DELAY);
            routesetInitializerService.startUpdating(UPDATE_ROUTESETS_DELAY);
            log.info("method=startUpdating Start updating in scheduled mode done");
        }

        private void startLazyUpdate() {
            log.info("method=startLazyUpdate Starting in lazy mode!");
            trainInitializerService.initializeInLazyMode();
            compositionInitializerService.initializeInLazyMode();
            trainRunningMessageInitializerService.initializeInLazyMode();
            routesetInitializerService.initializeInLazyMode();
            log.info("method=startLazyUpdate Starting in lazy mode done!");
        }

        private void initializeInLockMode() throws InterruptedException {
            log.info("method=initializeInLockMode Starting in lock mode!");

            systemStatePropertyService.setValue(ESystemStateProperty.DATABASE_LOCKED_MODE, Boolean.TRUE);
            log.debug("method=initializeInLockMode Marked locked mode to database!");

            final List<ExecutorService> executors = new ArrayList<>(4);

            log.info("method=initializeInLockMode Starting prefix={} initializeInLockMode", trainRunningMessageInitializerService.getPrefix());
            executors.add(trainRunningMessageInitializerService.initializeInLockMode());

            log.info("method=initializeInLockMode Starting prefix={} initializeInLockMode", routesetInitializerService.getPrefix());
            executors.add(routesetInitializerService.initializeInLockMode());

            // Update trains first to get timetables before running compositions update as timetables are needed for compositions update
            log.info("method=initializeInLockMode Starting and waiting prefix={} initializeInLockMode", trainInitializerService.getPrefix());
            waitUntilTasksAreDoneAndShutDown(trainInitializerService.initializeInLockMode(), log);

            log.info("method=initializeInLockMode Starting prefix={} initializeInLockMode", compositionInitializerService.getPrefix());
            executors.add(compositionInitializerService.initializeInLockMode());

            for (final ExecutorService executor : executors) {
                waitUntilTasksAreDoneAndShutDown(executor, log);
            }

            systemStatePropertyService.setValue(ESystemStateProperty.DATABASE_LOCKED_MODE, Boolean.FALSE);
            log.info("method=initializeInLockMode Ending initializeInLockMode");
        }

        private void clearDatabase() {
            log.info("method=clearDatabase Clearing database");
            trainInitializerService.clearEntities();
            compositionInitializerService.clearEntities();
            trainRunningMessageInitializerService.clearEntities();
            routesetInitializerService.clearEntities();
            log.info("method=clearDatabase Clearing database done");
        }

        private boolean isInitializationNeeded() {
            final long trainMaxVersion = trainPersistService.getMaxVersion();
            final long compositionMaxVersion = compositionService.getMaxVersion();
            final long trainRunningMessageMaxVersion = trainRunningMessageService.getMaxVersion();

            final boolean isInitializationNeeded = trainMaxVersion == 0 || compositionMaxVersion == 0 || trainRunningMessageMaxVersion == 0;
            log.info("method=isInitializationNeeded trainMaxVersion: {} == 0 | compositionMaxVersion: {} == 0 || trainRunningMessageMaxVersion: {} == 0 => isInitializationNeeded={}", trainMaxVersion, compositionMaxVersion, trainRunningMessageMaxVersion, isInitializationNeeded);

            return isInitializationNeeded;
        }
    }
}
