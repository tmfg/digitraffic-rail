package fi.livi.rata.avoindata.updater.service.hack;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RunningCurrentlyResetService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private DateProvider dateProvider;


//    @Scheduled(cron = "${updater.running-currently-reset-cron}", zone = "Europe/Helsinki")
    public void resetOldRunningTrains() {
        final LocalDate maxDepartureDate = dateProvider.dateInHelsinki().minusDays(2);
        trainLockExecutor.executeInLock("resetOldRunningTrains", () -> {
            final List<Train> oldRunningTrains = trainRepository.findRunningTrains(maxDepartureDate);
            final long maxVersion = trainRepository.getMaxVersion();

            for (final Train oldRunningTrain : oldRunningTrains) {
                oldRunningTrain.runningCurrently = false;
                oldRunningTrain.version = maxVersion + 1;

                log.info("Resetting running-currently for {} at version {}", oldRunningTrain.id, oldRunningTrain.version);
            }

            trainRepository.saveAll(oldRunningTrains);

            return maxVersion;
        });
    }
}
