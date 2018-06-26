package fi.livi.rata.avoindata.updater.controllers;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.CompositionInitializerService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.initializers.TrainInitializerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.AsyncRestTemplate;

import java.time.LocalDate;

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
    protected AsyncRestTemplate restTemplate;
    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private CompositionInitializerService compositionInitializerService;

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
    public void extractSchedules() {
        logger.info("Starting manual extract");
        scheduleService.extractSchedules();
    }
}
