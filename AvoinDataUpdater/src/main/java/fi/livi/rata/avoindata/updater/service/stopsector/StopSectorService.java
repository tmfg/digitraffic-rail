package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.stopsector.StopSectorQueueItemRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.stopsector.StopSectorQueueItem;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class StopSectorService {
    private final StopSectorUpdater stopSectorUpdater;

    private final StopSectorQueueItemRepository stopSectorQueueItemRepository;
    private final TrainRepository trainRepository;
    private final CompositionRepository compositionRepository;

    private final TrainLockExecutor trainLockExecutor;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final int ITEMS_TO_HANDLE = 40;

    public StopSectorService(final StopSectorUpdater stopSectorUpdater, final StopSectorQueueItemRepository stopSectorQueueItemRepository, final TrainRepository trainRepository, final CompositionRepository compositionRepository, final TrainLockExecutor trainLockExecutor) {
        this.stopSectorUpdater = stopSectorUpdater;
        this.stopSectorQueueItemRepository = stopSectorQueueItemRepository;
        this.trainRepository = trainRepository;
        this.compositionRepository = compositionRepository;
        this.trainLockExecutor = trainLockExecutor;
    }

    public void addTrains(final List<Train> trains, final String source) {
        stopSectorQueueItemRepository.saveAll(trains.stream()
                .filter(t -> !t.cancelled)
                .map(t -> new StopSectorQueueItem(t.id, source)).toList());
    }

    public void addCompositions(final List<Composition> compositions) {
        stopSectorQueueItemRepository.saveAll(compositions.stream().map(StopSectorQueueItem::new).toList());
    }

    private void handleItem(final StopSectorQueueItem item, final Composition composition) {
        final var train = trainRepository.findForSectorUpdate(item.departureDate, item.trainNumber);

        if (train == null) {
            log.error("could not find train for {}", item.id);
        } else {
            stopSectorUpdater.updateStopSectors(train, composition, item.source);
        }

        stopSectorQueueItemRepository.delete(item);
    }

    @Scheduled(fixedDelay = 700)
    public void handleStopSectorQueue() {
        TimingUtil.log(log, "handleStopSectorQueue", () -> {
            final var now = ZonedDateTime.now();
            final var items = stopSectorQueueItemRepository.findAllByOrderByCreated();
            final var maxAge = items.stream()
                    .map(item -> ChronoUnit.MILLIS.between(item.created, now))
                    .max(Long::compareTo).orElse(0L);

            log.info("method=handleStopSectorQueue maxAge={} queueSize={}", maxAge, items.size());

            // take ITEMS_TO_HANDLE items from the queue and process them with TrainLockExecutor
            items.stream().limit(ITEMS_TO_HANDLE).forEach(item -> {
                final var composition = compositionRepository.findById(new TrainId(item.trainNumber, item.departureDate));

                // train might not have yet got the composition
                composition.ifPresent(value -> trainLockExecutor.executeInTransactionLock("handleStopSectorQueue", () -> {
                    TimingUtil.log(log, "handleItem", () -> handleItem(item, value));

                    return null;
                }));
            });
        });
    }
}
