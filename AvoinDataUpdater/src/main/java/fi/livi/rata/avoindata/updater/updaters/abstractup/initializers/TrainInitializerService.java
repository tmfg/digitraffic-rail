package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;


import java.util.List;

import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.stopmonitoring.UdotUpdater;
import fi.livi.rata.avoindata.updater.service.stopmonitoring.UnknownDelayOrTrackUpdaterService;
import fi.livi.rata.avoindata.updater.service.stopsector.StopSectorService;
import fi.livi.rata.avoindata.updater.service.stopsector.StopSectorUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import fi.livi.rata.avoindata.updater.service.TrainPublishingService;
import fi.livi.rata.avoindata.updater.service.routeset.TimeTableRowByRoutesetUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

@Service
public class TrainInitializerService extends AbstractDatabaseInitializer<Train> {
    private final TrainPersistService trainPersistService;
    private final TrainLockExecutor trainLockExecutor;
    private final TrainPublishingService trainPublishingService;
    private final TimeTableRowByRoutesetUpdateService timeTableRowByRoutesetUpdateService;
    private final StopSectorUpdater stopSectorUpdater;
    private final UnknownDelayOrTrackUpdaterService unknownDelayOrTrackUpdaterService;

    private static final Logger log = LoggerFactory.getLogger(TrainInitializerService.class);

    public TrainInitializerService(final TrainPersistService trainPersistService, final TrainLockExecutor trainLockExecutor, final TrainPublishingService trainPublishingService, final TimeTableRowByRoutesetUpdateService timeTableRowByRoutesetUpdateService, final StopSectorUpdater stopSectorUpdater, final UnknownDelayOrTrackUpdaterService unknownDelayOrTrackUpdaterService) {
        this.trainPersistService = trainPersistService;
        this.trainLockExecutor = trainLockExecutor;
        this.trainPublishingService = trainPublishingService;
        this.timeTableRowByRoutesetUpdateService = timeTableRowByRoutesetUpdateService;
        this.stopSectorUpdater = stopSectorUpdater;
        this.unknownDelayOrTrackUpdaterService = unknownDelayOrTrackUpdaterService;
    }

    @Override
    public String getPrefix() {
        return "trains";
    }

    @Override
    public AbstractPersistService<Train> getPersistService() {
        return trainPersistService;
    }

    @Override
    protected Class<Train[]> getEntityCollectionClass() {
        return Train[].class;
    }

    @Override
    protected List<Train> doUpdate() {
        final var updatedTrains = trainLockExecutor.executeInLock(this.getPrefix(), super::doUpdate);

        trainPublishingService.publish(updatedTrains);

        return updatedTrains;
    }

    @Override
    public List<Train> modifyEntitiesBeforePersist(final List<Train> trains) {
        if (!trains.isEmpty()) {
            mergeUdots(trains);
            mergeStopSectors(trains);
            mergeRoutesets(trains);
        }

        return trains;
    }

    private void mergeUdots(final List<Train> trains) {
        TimingUtil.log(log, "mergeUdots", () -> unknownDelayOrTrackUpdaterService.mergeUdots(trains));
    }

    private void mergeStopSectors(final List<Train> trains) {
        TimingUtil.log(log, "mergeStopSectors", () -> stopSectorUpdater.mergeStopSectors(trains));
    }

    private void mergeRoutesets(final List<Train> trains) {
        TimingUtil.log(log, "mergeRoutesets", () -> timeTableRowByRoutesetUpdateService.updateByTrains(trains));
    }
}
