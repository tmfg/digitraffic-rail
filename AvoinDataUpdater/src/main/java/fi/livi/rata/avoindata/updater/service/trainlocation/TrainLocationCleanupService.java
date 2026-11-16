package fi.livi.rata.avoindata.updater.service.trainlocation;

import fi.livi.digitraffic.common.logging.MaintenanceLogger;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static fi.livi.digitraffic.common.logging.LogFields.durationSeconds;

/**
 * Removes old train locations periodically.
 */
@Service
public class TrainLocationCleanupService {
    private final TrainLocationRepository trainLocationRepository;

    public TrainLocationCleanupService(final TrainLocationRepository trainLocationRepository) {
        this.trainLocationRepository = trainLocationRepository;
    }

    @Scheduled(fixedRate = 1000 * 60 * 10, initialDelay = 1000 * 60 * 5)
    @Transactional
    public void deleteOldTrainLocations() {
        final StopWatch stopWatch = StopWatch.createStarted();

        try {
            final int deletedRows = trainLocationRepository.deleteOldestOverTwoYearsOld();

            MaintenanceLogger.logCleanup("train_location", deletedRows, TrainLocationRepository.PRUNE_BATCH_SIZE, durationSeconds(stopWatch.getTime()));
        } catch (final Exception e) {
            MaintenanceLogger.logError("train_location", durationSeconds(stopWatch.getTime()), e);
        }
    }
}
