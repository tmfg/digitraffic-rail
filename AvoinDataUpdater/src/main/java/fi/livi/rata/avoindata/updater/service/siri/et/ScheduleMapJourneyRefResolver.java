package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

public class ScheduleMapJourneyRefResolver implements JourneyRefResolver {

    private final Map<Long, Schedule> scheduleMap;
    private final NeTExEntityService entityService;
    private final NeTExIdGenerator idGenerator;

    public ScheduleMapJourneyRefResolver(final Map<Long, Schedule> scheduleMap,
                                         final NeTExEntityService entityService,
                                         final NeTExIdGenerator idGenerator) {
        this.scheduleMap = scheduleMap;
        this.entityService = entityService;
        this.idGenerator = idGenerator;
    }

    @Override
    public Optional<ResolvedJourney> resolve(final long trainNumber, final LocalDate departureDate) {
        final Schedule schedule = scheduleMap.get(trainNumber);
        if (schedule == null) {
            return Optional.empty();
        }
        final String serviceJourneyId = entityService.serviceJourneyIdFor(schedule);
        final String lineId = idGenerator.lineId(entityService.deriveLineId(schedule));
        return Optional.of(new ResolvedJourney(serviceJourneyId, departureDate.toString(), lineId));
    }
}
