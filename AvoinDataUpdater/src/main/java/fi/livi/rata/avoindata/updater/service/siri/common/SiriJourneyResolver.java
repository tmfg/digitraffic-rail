package fi.livi.rata.avoindata.updater.service.siri.common;

import java.time.LocalDate;
import java.util.Objects;

import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

/**
 * Maps a live train schedule to the published NeTEx ServiceJourney id and DataFrameRef.
 */
@Service
public class SiriJourneyResolver {

    private final NeTExIdGenerator idGenerator;

    public SiriJourneyResolver(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public ResolvedJourney resolveFromSchedule(final Schedule schedule, final LocalDate departureDate) {
        Objects.requireNonNull(schedule.trainNumber);
        final String serviceJourneyId;
        if (schedule.timetableType == Train.TimetableType.ADHOC) {
            serviceJourneyId = idGenerator.serviceJourneyIdAdhoc(schedule.trainNumber, schedule.startDate);
        } else {
            serviceJourneyId = idGenerator.serviceJourneyId(schedule.trainNumber, schedule.id);
        }
        final String lineIdentifier = (schedule.commuterLineId != null && !schedule.commuterLineId.isBlank())
                ? schedule.commuterLineId
                : schedule.trainType.name;
        final String lineId = idGenerator.lineId(lineIdentifier);
        return new ResolvedJourney(serviceJourneyId, departureDate.toString(), lineId);
    }
}
