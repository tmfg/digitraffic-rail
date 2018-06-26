package fi.livi.rata.avoindata.updater.service.hack;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.updater.controllers.ManualUpdateController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class DayInitializerSmokeService {
    public static final int MIN_TRAIN_TRESHOLD = 50;
    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private ManualUpdateController manualUpdateController;

    @Value("${updater.trains.numberOfFutureDaysToInitialize}")
    public int numberOfFutureDaysToInitialize;

    private Logger logger = LoggerFactory.getLogger(DayInitializerSmokeService.class);

    @Scheduled(cron = "${updater.force-initalization-check-cron}", zone="Europe/Helsinki")
    public void ensureAllDaysAreInitialized() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Helsinki"));
        for (int i = 0; i <= numberOfFutureDaysToInitialize; i++) {
            final LocalDate localDate = today.plusDays(i);

            final int countByDepartureDate = trainRepository.countByDepartureDate(localDate);
            logger.info("Checking day {} for proper initialization. Trains: {}", localDate, countByDepartureDate);

            if (countByDepartureDate < MIN_TRAIN_TRESHOLD) {
                logger.warn("Initializing day {} again by force because only {} trains were found", localDate, countByDepartureDate);
                manualUpdateController.reinitializeTrainsOnADate(localDate);
                logger.info("Initialization complete");
            }
        }
    }
}
