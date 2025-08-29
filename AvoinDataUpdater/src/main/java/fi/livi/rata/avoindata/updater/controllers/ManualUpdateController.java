package fi.livi.rata.avoindata.updater.controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
import fi.livi.rata.avoindata.updater.service.hack.OldTrainService;
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
    private OldTrainService oldTrainService;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @RequestMapping("/reinitialize")
    @ResponseBody
    public boolean reinitializeTrainsOnADate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        trainLockExecutor.executeInLock("manualReinitialize", () -> {
            logger.info("method=reinitializeTrainsOnADate Starting manual train update for day {}", date);

            trainRepository.removeByDepartureDate(date);
            trainRepository.flush();
            logger.info("method=reinitializeTrainsOnADate Removed existing trains");

            trainInitializerService.getAndSaveForADate(date);

            logger.info("method=reinitializeTrainsOnADate Finished manual train update for day {}", date);

            return date;
        });
        return true;
    }

    @RequestMapping("/reinitialize-compositions")
    @ResponseBody
    public boolean reinitializeCompositionsOnADate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        logger.info("method=reinitializeCompositionsOnADate Starting manual composition update for day {}", date);

        compositionRepository.removeByDepartureDate(date);
        compositionRepository.flush();
        logger.info("method=reinitializeCompositionsOnADate Removed existing compositions for day {}", date);

        compositionInitializerService.getAndSaveForADate(date);

        logger.info("method=reinitializeCompositionsOnADate Finished manual composition update for day {}", date);

        return true;
    }

    @RequestMapping("/extract")
    @ResponseBody
    public boolean extractSchedules() {
        logger.info("method=extractSchedules Starting manual extract");
        scheduleService.extractSchedules();
        logger.info("method=extractSchedules End manual extract");
        return true;
    }

    @RequestMapping("/gtfs")
    @ResponseBody
    public boolean generateGTFS() {
        logger.info("method=generateGTFS Starting manual gtfs");
        gtfsService.generateGTFS();
        logger.info("method=generateGTFS End manual gtfs");
        return true;
    }

    @RequestMapping("/gtfs-dev")
    @ResponseBody
    public boolean generateDevGTFS() throws ExecutionException, InterruptedException, IOException {
        logger.info("method=generateDevGTFS Starting manual gtfs");
        final LocalDate start = DateProvider.dateInHelsinki().minusDays(7);

        final Predicate<Schedule> lambda = s -> true;

        gtfsService.createGtfs(scheduleProviderService.getAdhocSchedules(start).stream().filter(lambda).collect(Collectors.toList()), scheduleProviderService.getRegularSchedules(start).stream().filter(lambda).collect(Collectors.toList()),"gtfs-test.zip",true);
        logger.info("method=generateDevGTFS End manual gtfs");
        return true;
    }

    @RequestMapping("/update-old-trains")
    @ResponseBody
    public boolean updateOldTrains() {
        logger.info("method=updateOldTrains Starting manual update");
        oldTrainService.updateOldTrains();
        logger.info("method=updateOldTrains End manual update");
        return true;
    }
}
