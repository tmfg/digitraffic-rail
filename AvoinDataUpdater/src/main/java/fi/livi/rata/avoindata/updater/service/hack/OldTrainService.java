package fi.livi.rata.avoindata.updater.service.hack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainSourceVersion;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

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

        log.info("method=updateOldTrains Starting to check for updated old trains from {} to {}", start, end);

        int updatedTotal = 0;

        try {
            for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
                final LocalDate departureDate = date;

                log.debug("method=updateOldTrains Checking for updated old trains. Date: {}", departureDate);

                final List<Train> trainResponse = getChangedTrains(departureDate);

                if (!trainResponse.isEmpty()) {
                    // updateEntities replaces source version with Digitraffic API version, so
                    // capture the payload version first
                    trainResponse.forEach(t -> t.sourceVersion = t.version);

                    updatedTotal += trainResponse.size();

                    trainLockExecutor.executeInLock("oldTrains", () -> {
                        log.info("method=updateOldTrains date={} updatedCount={} Updating: {}", departureDate,
                                trainResponse.size(),
                                Iterables.transform(trainResponse, t -> String.format("%s (%s)", t, t.sourceVersion)));

                        trainPersistService.updateEntities(trainResponse);

                        return trainResponse;
                    });

                    // sleep, so we don't block the train locker executor totally
                    try {
                        TimeUnit.MILLISECONDS.sleep(200);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            lastUpdateService.update(LastUpdateService.LastUpdatedType.OLD_TRAINS);

            log.info("method=updateOldTrains Finished checking old trains from {} to {} updatedTotal={}", start, end,
                    updatedTotal);
        } catch (final Exception e) {
            log.error("method=updateOldTrains Error while checking for updated old trains. updatedTotal={}",
                    updatedTotal, e);
            throw e;
        }
    }

    private List<Train> getChangedTrains(final LocalDate date) {
        final List<Train> changedTrains = new ArrayList<>();

        final List<TrainSourceVersion> trains = trainRepository.findSourceVersionsByDepartureDate(date);

        for (final List<TrainSourceVersion> oldTrainPartition : Lists.partition(trains, TRAINS_TO_FETCH_PER_QUERY)) {
            changedTrains.addAll(getChangedTrainsByIds(date, oldTrainPartition));
            try {
                Thread.sleep(400);
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return changedTrains;
    }

    private List<Train> getChangedTrainsByIds(final LocalDate date, final List<TrainSourceVersion> oldTrainPartition) {
        final Map<Long, Long> versions = new HashMap<>(oldTrainPartition.size());
        for (final TrainSourceVersion train : oldTrainPartition) {
            versions.put(train.getId().trainNumber, train.getSourceVersion());
        }

        final HashMap<String, Object> parts = new HashMap<>();
        parts.put("date", date.toString());
        parts.put("versions", versions);

        final List<Train> changedTrains = Arrays.asList(ripaService.postToRipa("old-trains", parts, Train[].class));

        log.info("method=getChangedTrainsByIds date={} askedCount={} changedCount={}", date, oldTrainPartition.size(),
                changedTrains.size());

        return changedTrains;
    }
}
