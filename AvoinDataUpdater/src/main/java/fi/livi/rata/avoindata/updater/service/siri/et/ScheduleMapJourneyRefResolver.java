package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriJourneyResolver;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

public class ScheduleMapJourneyRefResolver implements JourneyRefResolver {

    private final Map<Long, Schedule> scheduleMap;
    private final SiriJourneyResolver siriJourneyResolver;

    public ScheduleMapJourneyRefResolver(final Map<Long, Schedule> scheduleMap,
                                         final SiriJourneyResolver siriJourneyResolver) {
        this.scheduleMap = scheduleMap;
        this.siriJourneyResolver = siriJourneyResolver;
    }

    @Override
    public Optional<ResolvedJourney> resolve(final long trainNumber, final LocalDate departureDate) {
        final Schedule schedule = scheduleMap.get(trainNumber);
        if (schedule == null) {
            return Optional.empty();
        }
        return Optional.of(siriJourneyResolver.resolveFromSchedule(schedule, departureDate));
    }
}
