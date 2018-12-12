package fi.livi.rata.avoindata.updater;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.google.common.base.Strings;
import fi.livi.rata.avoindata.common.ESystemStateProperty;
import fi.livi.rata.avoindata.common.dao.CustomGeneralRepositoryImpl;
import fi.livi.rata.avoindata.common.service.SystemStatePropertyService;
import fi.livi.rata.avoindata.updater.service.CompositionService;
import fi.livi.rata.avoindata.updater.service.TrainRunningMessageService;
import fi.livi.rata.avoindata.updater.service.gtfs.GTFSService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.*;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

@SpringBootApplication
@EnableAutoConfiguration
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = {"fi.livi.rata.avoindata.updater", "fi.livi.rata.avoindata.common"})
@EntityScan(basePackages = "fi.livi.rata.avoindata.common.domain")
@EnableJpaRepositories(basePackages = "fi.livi.rata.avoindata.common.dao", repositoryBaseClass = CustomGeneralRepositoryImpl.class)
@EnableScheduling
public class DatabaseUpdaterApplication  {

    private static Logger log = LoggerFactory.getLogger(DatabaseUpdaterApplication.class);


    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));

        SpringApplication application = createApplication();

        application.run(args);
    }

    private static SpringApplication createApplication() {
        SpringApplication application = new SpringApplication(DatabaseUpdaterApplication.class);
        Properties properties = new Properties();

        properties.put("myHostname", getHostname());

        application.setDefaultProperties(properties);
        return application;
    }

    private static String getHostname() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }


    @Configuration
    public static class Runner implements CommandLineRunner {

        private final Logger log = LoggerFactory.getLogger(this.getClass());

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

        @Autowired
        private ForecastInitializerService forecastInitializerService;

        @Autowired
        private ScheduleService scheduleService;

        @Autowired
        private GTFSService gtfsService;

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
                log.info("updater.liikeinterface-url is null. Skipping initilization.");
                return;
            }


            AWSXRay.createSegment("initiliazing", (subsegment) -> {
                try {
                    if (isInitiliazationNeeded()) {
                        log.info("Database needs to be initiliazed!");
                        clearDatabase();
                        initializeInLockMode();
                        startLazyUpdate();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            startScheduleUpdates();

            startUpdating();
        }

        private void startScheduleUpdates() {
            SimpleAsyncTaskExecutor simpleAsyncTaskExecutor = new SimpleAsyncTaskExecutor();
            simpleAsyncTaskExecutor.execute(() -> {
                try {
                    gtfsService.generateGTFS();
                    scheduleService.extractSchedules();
                } catch (Exception e) {
                    log.error("Error creating GTFS.zip or Schedules", e);
                }
            });
        }

        private void startUpdating() {
            trainInitializerService.startUpdating(UPDATE_TRAINS_DELAY);
            compositionInitializerService.startUpdating(UPDATE_COMPOSITIONS_DELAY);
            trainRunningMessageInitializerService.startUpdating(UPDATE_TRAINRUNNINGMESSAGES_DELAY);
            //            routesetInitializerService.startUpdating(UPDATE_ROUTESETS_DELAY);
            forecastInitializerService.startUpdating(10000);
        }

        private void startLazyUpdate() {
            trainInitializerService.initializeInLazyMode();
            compositionInitializerService.initializeInLazyMode();
            trainRunningMessageInitializerService.initializeInLazyMode();
            //            routesetInitializerService.initializeInLazyMode();
            forecastInitializerService.initializeInLazyMode();
        }

        private void initializeInLockMode() throws InterruptedException {
            log.info("Starting in lock mode!");
            systemStatePropertyService.setValue(ESystemStateProperty.DATABASE_LOCKED_MODE, Boolean.TRUE);
            log.debug("Marked locked mode to database!");

            List<ExecutorService> executors = new ArrayList<>(5);
            executors.add(trainInitializerService.initializeInLockMode());
            executors.add(compositionInitializerService.initializeInLockMode());
            executors.add(trainRunningMessageInitializerService.initializeInLockMode());
            //            executors.add(routesetInitializerService.initializeInLockMode());

            for (final ExecutorService executor : executors) {
                AbstractDatabaseInitializer.waitUntilTasksAreDone(executor);
            }

            forecastInitializerService.initializeInLockMode();

            systemStatePropertyService.setValue(ESystemStateProperty.DATABASE_LOCKED_MODE, Boolean.FALSE);
            log.info("Ending in lock mode!");
        }

        private void clearDatabase() {
            log.info("Clearing database");
            trainInitializerService.clearEntities();
            compositionInitializerService.clearEntities();
            trainRunningMessageInitializerService.clearEntities();
            //            routesetInitializerService.clearEntities();
            forecastInitializerService.clearEntities();
        }

        private boolean isInitiliazationNeeded() {
            final long trainMaxVersion = trainPersistService.getMaxVersion();
            final long compositionMaxVersion = compositionService.getMaxVersion();
            final long trainRunningMessageMaxVersion = trainRunningMessageService.getMaxVersion();

            return trainMaxVersion == 0 || compositionMaxVersion == 0 || trainRunningMessageMaxVersion == 0;
        }
    }
}
