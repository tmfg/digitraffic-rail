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
        final List<Schedule> allSchedules = new ArrayList<>();

        allSchedules.addAll(getMostRecentRegularSchedules(date, regularSchedules));
        allSchedules.addAll(getMostRecentAdhocSchedules(date, adhocSchedules));

        final List<Schedule> todaysSchedules = Lists.newArrayList(Iterables.filter(allSchedules, s -> s.isAllowedByDates(date)));

        final List<Schedule> output = new ArrayList<>();
        final ImmutableListMultimap<Long, Schedule> trainNumberMap = Multimaps.index(todaysSchedules, s -> s.trainNumber);
        for (final Long trainNumber : trainNumberMap.keySet()) {
            final ImmutableList<Schedule> trainsSchedules = trainNumberMap.get(trainNumber);

            output.add(getAdhocScheduleInEffect(trainsSchedules));
        }

        return output;
    }

    private List<Schedule> getMostRecentRegularSchedules(final LocalDate date, final List<Schedule> regularSchedules) {
        final List<Schedule> output = new ArrayList<>();
        final Multimap<String, Schedule> capacityIdMap = Multimaps.index(regularSchedules, s -> s.capacityId);
        for (final String capacityId : capacityIdMap.keySet()) {
            final Collection<Schedule> schedulesByCapacityId = capacityIdMap.get(capacityId);
            final Schedule regularScheduleInEffect = getRegularScheduleInEffect(date, schedulesByCapacityId);
            if (regularScheduleInEffect != null) {
                output.add(regularScheduleInEffect);
            } else {
                log.trace("Cant decide best schedule. CapacityId: {} Schedules: {}", capacityId, schedulesByCapacityId);
            }
        }

        return output;
    }

    private List<Schedule> getMostRecentAdhocSchedules(final LocalDate date, final List<Schedule> adhocSchedules) {
        final List<Schedule> output = new ArrayList<>();

        final Iterable<Schedule> todaysAdhocSchedules = Iterables.filter(adhocSchedules, s -> s.isAllowedByDates(date));

        final ImmutableListMultimap<TrainId, Schedule> trainIdMap = Multimaps.index(todaysAdhocSchedules,
                s -> new TrainId(s.trainNumber, s.startDate));
        for (final TrainId trainId : trainIdMap.keySet()) {
            final ImmutableList<Schedule> schedulesForDay = trainIdMap.get(trainId);
            output.add(getAdhocScheduleInEffect(schedulesForDay));
        }

        return output;
    }

    private Schedule getAdhocScheduleInEffect(final Collection<Schedule> schedulesByCapacityId) {
        return Collections.max(schedulesByCapacityId, ADHOC_COMPARATOR);
    }

    /// if returns true, should select the second schedule
    private boolean compareSchedules(final Schedule s1, final Schedule s2) {
        final int cmp = s1.effectiveFrom.compareTo(s2.effectiveFrom);

        // same effective date, compare ids
        if(cmp == 0) {
            return s1.id < s2.id;
        }

        // otherwise return date comparison
        return cmp < 0;
    }

    private Schedule getRegularScheduleInEffect(final LocalDate date, final Collection<Schedule> schedulesByCapacityId) {
        Schedule chosenSchedule = null;
        for (final Schedule schedule : schedulesByCapacityId) {
            // schedule must not be effective after given date
            if(!schedule.effectiveFrom.isAfter(date)) {
                if(chosenSchedule == null || compareSchedules(chosenSchedule, schedule)) {
                    chosenSchedule = schedule;
                }
            }
        }

        if (chosenSchedule != null && chosenSchedule.changeType.equals("P")) {
            return null;
        } else {
            return chosenSchedule;
        }
    }
}
