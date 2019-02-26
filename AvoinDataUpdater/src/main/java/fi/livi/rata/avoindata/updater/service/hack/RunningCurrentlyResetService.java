package fi.livi.rata.avoindata.updater.service.hack;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
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


    @Transactional
    @Scheduled(cron = "${updater.running-currently-reset-cron}", zone = "Europe/Helsinki")
    public void resetOldRunningTrains() {
        LocalDate maxDepartureDate = dateProvider.dateInHelsinki().minusDays(2);
        trainLockExecutor.executeInLock(() -> {
            List<Train> oldRunningTrains = trainRepository.findRunningTrains(maxDepartureDate);
            long maxVersion = trainRepository.getMaxVersion();

            for (Train oldRunningTrain : oldRunningTrains) {
                oldRunningTrain.runningCurrently = false;
                oldRunningTrain.version = maxVersion + 1;

                log.info("Resetting running-currently for {}", oldRunningTrain.id);
            }

            trainRepository.saveAll(oldRunningTrains);

            return maxVersion;
        });
    }
}
