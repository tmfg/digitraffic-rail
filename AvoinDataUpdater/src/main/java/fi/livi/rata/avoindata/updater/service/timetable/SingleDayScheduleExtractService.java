package fi.livi.rata.avoindata.updater.service.timetable;

import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.common.dao.train.ExtractedScheduleRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.ExtractedSchedule;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SingleDayScheduleExtractService {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ScheduleToTrainConverter scheduleToTrainConverter;

    @Autowired
    private TrainPersistService trainPersistService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private ExtractedScheduleRepository extractedScheduleRepository;

    @Autowired
    private TodaysScheduleService todaysScheduleService;
    @Autowired
    private DateProvider dp;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<Train> extract(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules, final LocalDate date, final boolean shouldFakeVersions) {
        log.info("Parsing todays schedules for {}", date);
        final List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, adhocSchedules, regularSchedules);

        log.info("Extrating all possible schedules for {}", date);
        final Function<Train, TrainId> idFunc = s -> s.id;
        final Map<Train, Schedule> newTrains = scheduleToTrainConverter.extractSchedules(todaysSchedules, date);
        final Map<TrainId, Train> newTrainMap = Maps.uniqueIndex(newTrains.keySet(), idFunc::apply);

        log.info("Fetching existing trains for {}", date);
        final Map<TrainId, Train> oldTrainMap = Maps.uniqueIndex(trainRepository.findByDepartureDateFull(date), idFunc::apply);

        final List<Train> toBeAdded = new ArrayList<>();
        final List<Train> toBeUpdated = new ArrayList<>();
        final List<Train> toBeCancelled = new ArrayList<>();

        log.info("Grouping into lists for {}", date);
        groupIntoLists(newTrainMap, oldTrainMap, toBeAdded, toBeUpdated, toBeCancelled);

        printChanges(toBeAdded, toBeUpdated, toBeCancelled);

        log.info("Persisting trains for {}", date);
        persistTrains(date, toBeAdded, toBeUpdated, toBeCancelled, shouldFakeVersions);

        log.info("Extracted trains for {}. Total: {}, Today's Schedules: {} Added: {}, Updated: {}, Cancelled: {}", date,
                regularSchedules.size() + adhocSchedules.size(), todaysSchedules.size(), toBeAdded.size(), toBeUpdated.size(), toBeCancelled.size());

        final List<Train> allTrains = new ArrayList<>();
        allTrains.addAll(toBeAdded);
        allTrains.addAll(toBeUpdated);

        log.info("Creating ExtractedSchedules for {}", date);
        createExtractedSchedules(date, newTrains, allTrains);

        allTrains.addAll(toBeCancelled);

        return allTrains;
    }

    private void createExtractedSchedules(final LocalDate date, final Map<Train, Schedule> newTrains, final List<Train> allTrains) {
        final List<ExtractedSchedule> extractedSchedules = new ArrayList<>();
        for (final Train train : allTrains) {
            extractedSchedules.add(createExtractedSchedule(date, newTrains.get(train)));
        }
        extractedScheduleRepository.persist(extractedSchedules);
    }

    private void printChanges(final List<Train> toBeAdded, final List<Train> toBeUpdated, final List<Train> toBeCancelled) {
        if (!toBeAdded.isEmpty()) {
            log.info("Adding trains: {}", toBeAdded);
        }
        if (!toBeUpdated.isEmpty()) {
            log.info("Updating trains: {}", toBeUpdated);
        }
        if (!toBeCancelled.isEmpty()) {
            log.info("Cancelling trains: {}", toBeCancelled);
        }
    }

    private boolean areTrainsEqual(final Train left, final Train right) {
        if (left.deleted == null && right.deleted != null) {
            return false;
        } else if (left.deleted != null && right.deleted == null) {
            return false;
        }

        if (!left.id.equals(right.id)) {
            throw new IllegalStateException("Comparing completely different trains" + left + right);
        }

        if (left.cancelled != right.cancelled) {
            return false;
        }

        if (!left.timetableType.equals(right.timetableType) ){
            return false;
        }

        if (left.timeTableRows.size() != right.timeTableRows.size()) {
            return false;
        }

        if (left.operator.operatorUICCode != right.operator.operatorUICCode) {
            return false;
        }

        if (left.trainCategoryId != right.trainCategoryId) {
            return false;
        }

        if (left.trainTypeId != right.trainTypeId) {
            return false;
        }

        if (!Objects.equals(left.commuterLineID, right.commuterLineID)) {
            return false;
        }

        final List<TimeTableRow> sortedLeftTimeTableRows = sortTimeTableRows(left.timeTableRows);
        final List<TimeTableRow> sortedRightTimeTableRows = sortTimeTableRows(right.timeTableRows);


        for (int i = 0; i < left.timeTableRows.size(); i++) {
            final TimeTableRow leftTimeTableRow = sortedLeftTimeTableRows.get(i);
            final TimeTableRow rightTimeTableRow = sortedRightTimeTableRows.get(i);

            if (areTimeTableRowsNotEqual(leftTimeTableRow, rightTimeTableRow)) {
                return false;
            }
        }

        return true;
    }

    private boolean areTimeTableRowsNotEqual(final TimeTableRow leftTimeTableRow, final TimeTableRow rightTimeTableRow) {
        if (leftTimeTableRow.cancelled != rightTimeTableRow.cancelled) {
            return true;
        }

        if (!leftTimeTableRow.type.equals(rightTimeTableRow.type)) {
            return true;
        }

        if (!leftTimeTableRow.scheduledTime.withZoneSameInstant(ZoneId.of("UTC")).equals(
                rightTimeTableRow.scheduledTime.withZoneSameInstant(ZoneId.of("UTC")))) {
            return true;
        }

        if (!leftTimeTableRow.station.stationShortCode.equals(rightTimeTableRow.station.stationShortCode)) {
            return true;
        }

        if (!Objects.equals(leftTimeTableRow.commercialStop, rightTimeTableRow.commercialStop)) {
            return true;
        }

        return false;
    }

    private List<TimeTableRow> sortTimeTableRows(final List<TimeTableRow> timeTableRows) {
        return timeTableRows.stream().sorted((o1, o2) -> {
            final int i = o1.scheduledTime.compareTo(o2.scheduledTime);
            if (i == 0) {
                return o1.type.compareTo(o2.type);
            } else {
                return i;
            }
        }).collect(Collectors.toList());
    }


    private List<Train> persistTrains(final LocalDate date, final List<Train> toBeAdded, final List<Train> toBeUpdated,
                                      final List<Train> toBeCancelled, final boolean shouldFakeVersions) {
        final List<Train> changedtrains = new ArrayList<>();

        final long fakeVersion = trainPersistService.getMaxVersion() + 1;
        log.info("Using fakeVersion {}", fakeVersion);

        if (!toBeAdded.isEmpty()) {
            if (shouldFakeVersions) {
                for (final Train train : toBeAdded) {
                    train.version = fakeVersion;
                }
            }
            changedtrains.addAll(toBeAdded);

            trainPersistService.addEntities(toBeAdded);
        }

        if (!toBeUpdated.isEmpty()) {
            if (shouldFakeVersions) {
                for (final Train train : toBeUpdated) {
                    train.version = fakeVersion;
                }
            }
            changedtrains.addAll(toBeUpdated);

            trainPersistService.updateEntities(toBeUpdated);
        }

        if (!toBeCancelled.isEmpty()) {
            final List<Train> trainsCancelled = new ArrayList<>();
            for (final Train trainToBeCancelled : toBeCancelled) {
                trainToBeCancelled.deleted = true;
                trainToBeCancelled.cancelled = true;
                for (final TimeTableRow timeTableRow : trainToBeCancelled.timeTableRows) {
                    timeTableRow.cancelled = true;
                }
                trainsCancelled.add(trainToBeCancelled);
            }

            if (shouldFakeVersions) {
                for (final Train train : trainsCancelled) {
                    train.version = fakeVersion;
                }
            }
            changedtrains.addAll(trainsCancelled);

            trainPersistService.updateEntities(trainsCancelled);
        }

        return changedtrains;
    }


    private void groupIntoLists(final Map<TrainId, Train> newTrainMap, final Map<TrainId, Train> oldTrainMap, final List<Train> toBeAdded,
                                final List<Train> toBeUpdated, final List<Train> toBeCancelled) {
        for (final TrainId newTrainId : newTrainMap.keySet()) {
            final Train newTrain = newTrainMap.get(newTrainId);
            final Train oldTrain = oldTrainMap.get(newTrainId);

            if (oldTrain == null) {
                if (!newTrain.cancelled) {
                    toBeAdded.add(newTrain);
                }
            } else {
                if (!areTrainsEqual(newTrain, oldTrain)) {
                    entityManager.detach(oldTrain);

                    for (final TimeTableRow timeTableRow : oldTrain.timeTableRows) {
                        entityManager.detach(timeTableRow);
                    }

                    toBeUpdated.add(newTrain);
                }
            }
        }

        for (final TrainId oldTrainId : oldTrainMap.keySet()) {
            final Train newTrain = newTrainMap.get(oldTrainId);
            final Train oldTrain = oldTrainMap.get(oldTrainId);

            if (newTrain == null && !oldTrain.cancelled) {
                toBeCancelled.add(oldTrain);
            }
        }
    }

    private ExtractedSchedule createExtractedSchedule(final LocalDate date, final Schedule schedule) {
        final ExtractedSchedule extractedSchedule = new ExtractedSchedule();
        extractedSchedule.scheduleId = schedule.id;
        extractedSchedule.capacityId = schedule.capacityId;
        extractedSchedule.trainId = new TrainId(schedule.trainNumber, date);
        extractedSchedule.timestamp = dp.nowInHelsinki();
        extractedSchedule.version = schedule.version;

        return extractedSchedule;
    }
}
