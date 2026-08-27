package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
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
        final String operatorRef = schedule.operator != null
                ? idGenerator.operatorId(schedule.operator.operatorShortCode)
                : null;
        return Optional.of(new ResolvedJourney(
                new ServiceJourneyId(serviceJourneyId),
                new DataFrameRef(departureDate.toString()),
                new LineId(lineId),
                operatorRef != null ? new OperatorRef(operatorRef) : null));
    }
}
