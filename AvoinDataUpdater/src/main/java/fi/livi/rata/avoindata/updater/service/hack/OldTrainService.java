package fi.livi.rata.avoindata.updater.service.hack;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class OldTrainService {
    public static final int TRAINS_TO_FETCH_PER_QUERY = 250;
    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private RipaService ripaService;

    @Autowired
    private TrainPersistService trainPersistService;

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private LastUpdateService lastUpdateService;

    @Value("${updater.trains.numberOfPastDaysToInitialize}")
    private Integer numberOfDaysToInitialize;

    private final Logger log = LoggerFactory.getLogger(OldTrainService.class);

    @Scheduled(cron = "${updater.oldtrainupdater-check-cron}", zone = "Europe/Helsinki")
    public void updateOldTrains() {
        final LocalDate end = LocalDate.now().minusDays(2);
        final LocalDate start = LocalDate.now().minusDays(numberOfDaysToInitialize);

        log.info("Starting to check for updated old trains from {} to {}", start, end);

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            log.info("Checking for updated old trains. Date: {}", date);

            final List<Train> trainResponse = getChangedTrains(date);

            trainLockExecutor.executeInLock("oldTrains", () -> {

                if (!trainResponse.isEmpty()) {
                    log.info("Updating: {}", Iterables.transform(trainResponse, t -> String.format("%s (%s)", t, t.version)));

                    final long maxVersion = trainRepository.getMaxVersion();
                    for (final Train train : trainResponse) {
                        train.version = maxVersion + 1;
                    }

                    trainPersistService.updateEntities(trainResponse);
                }

                return trainResponse;
            });
        }

        lastUpdateService.update(LastUpdateService.LastUpdatedType.OLD_TRAINS);
    }

    private List<Train> getChangedTrains(final LocalDate date) {
        final List<Train> changedTrains = new ArrayList<>();

        final List<Object[]> trains = trainRepository.findByDepartureDateLite(date);

        for (final List<Object[]> oldTrainPartition : Lists.partition(trains, TRAINS_TO_FETCH_PER_QUERY)) {
            changedTrains.addAll(getChangedTrainsByIds(date, oldTrainPartition));
            try {
                Thread.sleep(400);
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return changedTrains;
    }

    private List<Train> getChangedTrainsByIds(final LocalDate date, final List<Object[]> oldTrainPartition) {
        final Map<Long, Long> versions = new HashMap<>(oldTrainPartition.size());
        for (final Object[] train : oldTrainPartition) {
            final TrainId id = (TrainId) train[0];
            final Long version = (Long) train[1];
            versions.put(id.trainNumber, version);
        }

        final HashMap<String, Object> parts = new HashMap<>();
        parts.put("date", date);
        parts.put("versions", versions);

        log.info("Fetching {} changed trains for {}", oldTrainPartition.size(), date);

        return Arrays.asList(ripaService.postToRipa("old-trains", parts, Train[].class));
    }
}
