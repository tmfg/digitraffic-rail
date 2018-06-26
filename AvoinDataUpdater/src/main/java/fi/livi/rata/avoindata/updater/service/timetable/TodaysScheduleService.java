package fi.livi.rata.avoindata.updater.service.timetable;

import com.google.common.collect.*;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class TodaysScheduleService {
    private Logger log = LoggerFactory.getLogger(getClass());
    public static final Comparator<Schedule> ADHOC_COMPARATOR = Comparator.comparingLong(i -> i.id);

    public List<Schedule> getDaysSchedules(final LocalDate date, final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules) {
        List<Schedule> allSchedules = new ArrayList<>();

        allSchedules.addAll(getMostRecentRegularSchedules(date, regularSchedules));
        allSchedules.addAll(getMostRecentAdhocSchedules(date, adhocSchedules));

        final List<Schedule> todaysSchedules = Lists.newArrayList(Iterables.filter(allSchedules, s -> s.isAllowedByDates(date)));

        List<Schedule> output = new ArrayList<>();
        final ImmutableListMultimap<Long, Schedule> trainNumberMap = Multimaps.index(todaysSchedules, s -> s.trainNumber);
        for (final Long trainNumber : trainNumberMap.keySet()) {
            final ImmutableList<Schedule> trainsSchedules = trainNumberMap.get(trainNumber);

            output.add(getMostRecentSchedule(trainsSchedules));
        }

        return output;
    }

    private List<Schedule> getMostRecentRegularSchedules(final LocalDate date, final List<Schedule> regularSchedules) {
        List<Schedule> output = new ArrayList<>();
        final Multimap<String, Schedule> capacityIdMap = Multimaps.index(regularSchedules, s -> s.capacityId);
        for (final String capacityId : capacityIdMap.keySet()) {
            final Collection<Schedule> schedulesByCapacityId = capacityIdMap.get(capacityId);
            final Schedule bestRegularSchedule = getBestRegularSchedule(date, schedulesByCapacityId);
            if (bestRegularSchedule != null) {
                output.add(bestRegularSchedule);
            } else {
                log.trace("Cant decide best schedule. CapacityId: {} Schedules: {}", capacityId, schedulesByCapacityId);
            }
        }

        return output;
    }

    private List<Schedule> getMostRecentAdhocSchedules(LocalDate date, final List<Schedule> adhocSchedules) {
        List<Schedule> output = new ArrayList<>();

        final Iterable<Schedule> todaysAdhocSchedules = Iterables.filter(adhocSchedules, s -> s.isAllowedByDates(date));

        final ImmutableListMultimap<TrainId, Schedule> trainIdMap = Multimaps.index(todaysAdhocSchedules,
                s -> new TrainId(s.trainNumber, s.startDate));
        for (final TrainId trainId : trainIdMap.keySet()) {
            final ImmutableList<Schedule> schedulesForDay = trainIdMap.get(trainId);
            output.add(getMostRecentSchedule(schedulesForDay));
        }

        return output;
    }

    private Schedule getMostRecentSchedule(final Collection<Schedule> schedulesByCapacityId) {
        return Collections.max(schedulesByCapacityId, ADHOC_COMPARATOR);
    }

    private Schedule getBestRegularSchedule(final LocalDate date, final Collection<Schedule> schedulesByCapacityId) {
        Schedule chosenSchedule = null;
        for (final Schedule schedule : schedulesByCapacityId) {
            final boolean isIdNewer = chosenSchedule == null || chosenSchedule.id < schedule.id;
            final boolean isEffectiveDateBeforeDate = schedule.effectiveFrom.isBefore(date) || schedule.effectiveFrom.isEqual(date);
            if (isIdNewer && isEffectiveDateBeforeDate) {
                chosenSchedule = schedule;
            }
        }

        if (chosenSchedule != null && chosenSchedule.changeType.equals("P")) {
            return null;
        } else {
            return chosenSchedule;
        }
    }
}
