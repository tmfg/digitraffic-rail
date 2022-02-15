package fi.livi.rata.avoindata.updater.controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import fi.livi.rata.avoindata.updater.service.gtfs.GTFSRealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.gtfs.GTFSService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.CompositionInitializerService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.TrainInitializerService;

@Controller
public class ManualUpdateController {
    @Autowired
    private TrainInitializerService trainInitializerService;
    @Autowired
    private TrainRepository trainRepository;
    @Value("${updater.schedule-extracting.days-to-extract:365}")
    protected Integer numberOfDaysToExtract;
    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private CompositionInitializerService compositionInitializerService;

    @Autowired
    private GTFSService gtfsService;

    @Autowired
    private ScheduleProviderService scheduleProviderService;

    @Autowired
    private GTFSRealtimeService gtfsRealtimeService;

    @Autowired
    private DateProvider dp;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @RequestMapping("/reinitialize")
    @ResponseBody
    public boolean reinitializeTrainsOnADate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        trainLockExecutor.executeInLock(() -> {
            logger.info("Starting manual train update for day {}", date);

            trainRepository.removeByDepartureDate(date);
            trainRepository.flush();
            logger.info("Removed existing trains");

            trainInitializerService.getAndSaveForADate(date);

            logger.info("Finished manual train update for day {}", date);

            return date;
        });
        return true;
    }

    @RequestMapping("/reinitialize-compositions")
    @ResponseBody
    public boolean reinitializeCompositionsOnADate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        logger.info("Starting manual composition update for day {}", date);

        compositionRepository.removeByDepartureDate(date);
        compositionRepository.flush();
        logger.info("Removed existing compositions");

        compositionInitializerService.getAndSaveForADate(date);

        logger.info("Finished manual composition update for day {}", date);

        return true;
    }

    @RequestMapping("/extract")
    @ResponseBody
    public boolean extractSchedules() {
        logger.info("Starting manual extract");
        scheduleService.extractSchedules();

        return true;
    }

    @RequestMapping("/gtfs")
    @ResponseBody
    public boolean generateGTFS() {
        logger.info("Starting manual gtfs");
        gtfsService.generateGTFS();
        return true;
    }

    @RequestMapping("/gtfs-dev")
    @ResponseBody
    public boolean generateDevGTFS() throws ExecutionException, InterruptedException, IOException {
        logger.info("Starting manual gtfs");
        final LocalDate start = dp.dateInHelsinki().minusDays(7);

        gtfsService.createVRTreGtfs(scheduleProviderService.getAdhocSchedules(start).stream().filter(s->this.isPassengerTrain(s)).collect(Collectors.toList()), scheduleProviderService.getRegularSchedules(start).stream().filter(s->this.isPassengerTrain(s)).collect(Collectors.toList()));
        return true;
    }

    @RequestMapping("/gtfs-locations")
    @ResponseBody
    public boolean generateGTFSLocations() {
        logger.info("Starting manual gtfs vehicle location feed");

        logger.info(gtfsRealtimeService.createVehiceLocationFeedMessage().toString());

        return true;
    }

    @RequestMapping("/gtfs-realtime")
    @ResponseBody
    public boolean generateGTFSRealtime() {
        logger.info("Starting manual gtfs trip update feed");

        logger.info(gtfsRealtimeService.createTripUpdateFeedMessage().toString());

        return true;
    }

    private boolean isPassengerTrain(Schedule s) {
        return s.trainCategory.name.equals("Commuter") || (s.trainCategory.name.equals("Long-distance") && s.trainType.commercial == true);
    }
}
