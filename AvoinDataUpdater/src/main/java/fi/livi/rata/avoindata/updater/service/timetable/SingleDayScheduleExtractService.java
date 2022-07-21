package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Function;
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
    public List<Train> extract(final List<Schedule> allSchedules, final LocalDate date, boolean shouldFakeVersions) {
        List<Schedule> adhocSchedules = new ArrayList<>();
        List<Schedule> regularSchedules = new ArrayList<>();

        for (final Schedule schedule : allSchedules) {
            if (schedule.timetableType == Train.TimetableType.REGULAR) {
                regularSchedules.add(schedule);
            } else if (schedule.timetableType == Train.TimetableType.ADHOC) {
                adhocSchedules.add(schedule);
            } else {
                throw new IllegalArgumentException("Unknown timetableType" + schedule.timetableType);
            }
        }

        log.info("Parsing todays schedules for {}", date);
        List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, adhocSchedules, regularSchedules);

        log.info("Extrating all possible schedules for {}", date);
        final Function<Train, TrainId> idFunc = s -> s.id;
        final Map<Train, Schedule> newTrains = scheduleToTrainConverter.extractSchedules(todaysSchedules, date);
        final Map<TrainId, Train> newTrainMap = Maps.uniqueIndex(newTrains.keySet(), idFunc);

        log.info("Fetching existing trains for {}", date);
        final Map<TrainId, Train> oldTrainMap = Maps.uniqueIndex(trainRepository.findByDepartureDateFull(date), idFunc);

        List<Train> toBeAdded = new ArrayList<>();
        List<Train> toBeUpdated = new ArrayList<>();
        List<Train> toBeCancelled = new ArrayList<>();

        log.info("Grouping into lists for {}", date);
        groupIntoLists(newTrainMap, oldTrainMap, toBeAdded, toBeUpdated, toBeCancelled);

        printChanges(toBeAdded, toBeUpdated, toBeCancelled);

        log.info("Persisting trains for {}", date);
        persistTrains(date, toBeAdded, toBeUpdated, toBeCancelled, shouldFakeVersions);

        log.info("Extracted trains for {}. Total: {}, Todays Schedules: {} Added: {}, Updated: {}, Cancelled: {}", date,
                allSchedules.size(), todaysSchedules.size(), toBeAdded.size(), toBeUpdated.size(), toBeCancelled.size());

        List<Train> allTrains = new ArrayList<>();
        allTrains.addAll(toBeAdded);
        allTrains.addAll(toBeUpdated);

        log.info("Creating ExtractedSchedules for {}", date);
        createExtractedSchedules(date, newTrains, allTrains);

        allTrains.addAll(toBeCancelled);

        return allTrains;
    }

    private void createExtractedSchedules(final LocalDate date, final Map<Train, Schedule> newTrains, final List<Train> allTrains) {
        List<ExtractedSchedule> extractedSchedules = new ArrayList<>();
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

        if (!left.timetableAcceptanceDate.equals(right.timetableAcceptanceDate)) {
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

        List<TimeTableRow> sortedLeftTimeTableRows = sortTimeTableRows(left.timeTableRows);
        List<TimeTableRow> sortedRightTimeTableRows = sortTimeTableRows(right.timeTableRows);


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
        List<Train> changedtrains = new ArrayList<>();

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
            List<Train> trainsCancelled = new ArrayList<>();
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
                if (newTrain.cancelled == false) {
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

            if (newTrain == null && oldTrain.cancelled == false) {
                toBeCancelled.add(oldTrain);
            }
        }
    }

    private ExtractedSchedule createExtractedSchedule(final LocalDate date, final Schedule schedule) {
        ExtractedSchedule extractedSchedule = new ExtractedSchedule();
        extractedSchedule.scheduleId = schedule.id;
        extractedSchedule.capacityId = schedule.capacityId;
        extractedSchedule.trainId = new TrainId(schedule.trainNumber, date);
        extractedSchedule.timestamp = dp.nowInHelsinki();
        extractedSchedule.version = schedule.version;

        return extractedSchedule;
    }
}
