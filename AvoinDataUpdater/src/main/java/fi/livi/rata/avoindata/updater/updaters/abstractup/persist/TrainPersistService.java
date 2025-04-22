package fi.livi.rata.avoindata.updater.updaters.abstractup.persist;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import fi.livi.rata.avoindata.common.dao.stopsector.StopSectorQueueItemRepository;
import fi.livi.rata.avoindata.common.domain.stopsector.StopSectorQueueItem;
import fi.livi.rata.avoindata.updater.service.stopsector.StopSectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Iterables;
import fi.livi.rata.avoindata.common.dao.cause.CauseRepository;
import fi.livi.rata.avoindata.common.dao.train.FindByTrainIdService;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainReadyRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.train.TrainReady;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.AbstractPersistService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class TrainPersistService extends AbstractPersistService<Train> {
    private final TrainRepository trainRepository;
    private final TimeTableRowRepository timeTableRowRepository;
    private final CauseRepository causeRepository;
    private final TrainReadyRepository trainReadyRepository;

    private final StopSectorService serviceStopSector;
    private final BatchExecutionService bes;
    private final FindByTrainIdService findByTrainIdService;
    private final StopSectorService stopSectorService;

    @PersistenceContext
    private EntityManager entityManager;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public TrainPersistService(final TrainRepository trainRepository, final TimeTableRowRepository timeTableRowRepository, final CauseRepository causeRepository, final TrainReadyRepository trainReadyRepository, final StopSectorService serviceStopSector, final BatchExecutionService bes, final FindByTrainIdService findByTrainIdService, final StopSectorService stopSectorService) {
        this.trainRepository = trainRepository;
        this.timeTableRowRepository = timeTableRowRepository;
        this.causeRepository = causeRepository;
        this.trainReadyRepository = trainReadyRepository;
        this.serviceStopSector = serviceStopSector;
        this.bes = bes;
        this.findByTrainIdService = findByTrainIdService;
        this.stopSectorService = stopSectorService;
    }

    @Override
    @Transactional
    public synchronized List<Train> updateEntities(final List<Train> entities) {
        if (entities.isEmpty()) {
            return entities;
        }


        for (final Train entity : entities) {
            for (final TimeTableRow timeTableRow : entity.timeTableRows) {
                for (final Cause cause : timeTableRow.causes) {
                    entityManager.detach(cause);
                }
                for (final TrainReady trainReady : timeTableRow.trainReadies) {
                    entityManager.detach(trainReady);
                }
                entityManager.detach(timeTableRow);
            }
            entityManager.detach(entity);
        }

        final List<TrainId> trainIds = entities.stream().map(s->s.id).sorted(TrainId::compareTo).collect(Collectors.toList());

        bes.consume(trainIds, findByTrainIdService::removeByTrainId);

        addEntities(entities);

        for (final Train entity : entities) {
            if (entity.version > maxVersion.get()) {
                maxVersion.set(entity.version);
            }
        }

        return entities;
    }

    @Override
    public void clearEntities() {
        trainRepository.deleteAllInBatch();
    }

    @Override
    @Transactional
    public void addEntities(final List<Train> entities) {
        final List<TrainReady> trainReadies = new ArrayList<>();
        final List<TimeTableRow> timeTableRows = new ArrayList<>();
        final List<Cause> causes = new ArrayList<>();

        for (final Train train : entities) {
            final TimeTableRow lastTimeTableRow = Iterables.getLast(train.timeTableRows);
            if (lastTimeTableRow.type == TimeTableRow.TimeTableRowType.DEPARTURE) {
                /* In Liike, a train can end at "OL__V330" for example (a station which is not shown in DT).
                 This results in a broken train if we do not delete the second last stop */
                log.warn("Removed last timetablerow {} because it was a departure", lastTimeTableRow);
                train.timeTableRows.remove(lastTimeTableRow);
            }

            for (final TimeTableRow timeTableRow : train.timeTableRows) {
                if (!timeTableRow.trainReadies.isEmpty()) {
                    trainReadies.addAll(timeTableRow.trainReadies);
                }
                if (!timeTableRow.causes.isEmpty()) {
                    causes.addAll(timeTableRow.causes);
                }

                timeTableRows.add(timeTableRow);
            }
        }

        stopSectorService.addTrains(entities, "Train");

        trainRepository.persist(entities);
        timeTableRowRepository.persist(timeTableRows);
        trainReadyRepository.persist(trainReadies);
        causeRepository.persist(causes);
    }

    public Long getMaxVersion() {
        return trainRepository.getMaxVersion();
    }
}
