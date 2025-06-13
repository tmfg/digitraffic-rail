package fi.livi.rata.avoindata.updater.service.stopmonitoring;

import fi.livi.rata.avoindata.common.dao.stopmonitoring.UdotRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.stopmonitoring.UdotData;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UnknownDelayOrTrackUpdaterService {
    private static final Logger log = LoggerFactory.getLogger(UnknownDelayOrTrackUpdaterService.class);

    private final UdotRepository udotRepository;
    private final TrainRepository trainRepository;
    private final TrainLockExecutor trainLockExecutor;

    public UnknownDelayOrTrackUpdaterService(final UdotRepository udotRepository, final TrainRepository trainRepository, final TrainLockExecutor trainLockExecutor) {
        this.udotRepository = udotRepository;
        this.trainRepository = trainRepository;
        this.trainLockExecutor = trainLockExecutor;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional(readOnly = true)
    public void updateUdotInformation() {
        log.info("Starting updateUdotInformation");

        TimingUtil.log(log, "updateUdotInformation", this::handleUdot);
    }

    private void handleUdot() {
        // get all udot data, that have not yet been updated to Trains and TimeTableRows
        final List<UdotData> unhandled = udotRepository.findByModelUpdatedTimeIsNullOrderByModifiedDb();

        if(!unhandled.isEmpty()) {
            log.info("Handling udot, count={}", unhandled.size());

            // updating TimeTableRows must be done in transactionlock, using new transaction for each udot,
            // minimizing database locking for both time_table_row and rami_udot
            unhandled.forEach(u -> trainLockExecutor.executeInTransactionLock("UDOT", () -> {
                // get train
                final Optional<Train> train = trainRepository.findById(new TrainId(u.getTrainNumber(), u.getTrainDepartureDate()));

                if (train.isPresent()) {
                    final long maxVersion = trainRepository.getMaxVersion();

                    // update model
                   UdotUpdater.updateUdotInformation(u, train.get(), maxVersion + 1);
                } else {
                    log.error("Could not find train {} {}", u.getTrainNumber(), u.getTrainDepartureDate());
                }

                // set model updated for udot
                udotRepository.setModelUpdated(u.getTrainDepartureDate(), u.getAttapId(), u.getModifiedDb());
                return null;
            }));
        }
    }

    /**
     * Get current stop sectors from the database for given trains and update them(train come from integration
     * and do not have the stop sectors, that's why we merge them from previous values)
     */
    public void mergeUdots(final List<Train> trains) {
        trains.forEach(t -> {
            final var udots = udotRepository.getRowsWithUdot(t.id.departureDate, t.id.trainNumber);

            if(!udots.isEmpty()) {
                // map attapId -> udot
                final var udotMap = udots.stream()
                        .collect(Collectors.toMap(v -> v[0], v -> Pair.of((Boolean)v[1], (Boolean)v[2])));

                t.timeTableRows.forEach(row -> {
                    final var pair = udotMap.get(row.id.attapId);

                    if(pair != null) {
                        if(pair.getLeft() != null) {
                            row.unknownDelay = pair.getLeft();
                        }
                        if(pair.getRight() != null) {
                            row.unknownTrack = pair.getRight();
                        }
                    }
                });
            }
        });
    }
}
