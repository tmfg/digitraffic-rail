package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.TrackObservation;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Track a train was last seen to use, for stops the schedule and the coming days
 * leave without one. Only stops the train was booked to make and that were not
 * cancelled count.
 */
@Component
public class HistoricalTrackSource {

    private static final Logger log = LoggerFactory.getLogger(HistoricalTrackSource.class);

    private final TimeTableRowRepository timeTableRowRepository;

    @Value("${updater.netex.track-history-days:14}")
    private int historyDays;

    public HistoricalTrackSource(final TimeTableRowRepository timeTableRowRepository) {
        this.timeTableRowRepository = timeTableRowRepository;
    }

    /**
     * Asks only about the trains that have a gap, because reading whole days would
     * drag the rest of the country's timetable through the generation transaction.
     */
    public Map<StopKey, String> resolve(final Set<StopKey> wanted) {
        final Map<StopKey, String> found = new HashMap<>();
        if (wanted.isEmpty()) {
            return found;
        }

        final Set<Long> trainNumbers = wanted.stream().map(StopKey::trainNumber).collect(Collectors.toSet());
        final LocalDate today = DateProvider.dateInHelsinki();
        final List<TrackObservation> observations = timeTableRowRepository.findObservedTracks(
                today.minusDays(historyDays), today.minusDays(1), trainNumbers);

        for (final TrackObservation observation : observations) {
            if (StringUtils.isBlank(observation.commercialTrack())) {
                continue;
            }
            for (final StopKey key : keysAnsweredBy(observation)) {
                if (wanted.contains(key)) {
                    // newest first, so the first answer for a key is the most recent
                    found.putIfAbsent(key, observation.commercialTrack());
                }
            }
        }

        log.info("method=resolve wanted={} trains={} observations={} resolved={} days={}",
                wanted.size(), trainNumbers.size(), observations.size(), found.size(), historyDays);
        return found;
    }

    private static List<StopKey> keysAnsweredBy(final TrackObservation observation) {
        return List.of(
                forSchedulePart(observation.trainNumber(), observation.attapId(), observation.type()),
                forStation(observation.trainNumber(), observation.stationShortCode(), observation.type()));
    }

    /** Keyed on attapId to pin the same route, or on station to survive a timetable change. */
    public static StopKey forSchedulePart(final long trainNumber, final long attapId,
            final TimeTableRow.TimeTableRowType type) {
        return new StopKey(trainNumber, String.valueOf(attapId), type);
    }

    public static StopKey forStation(final long trainNumber, final String stationShortCode,
            final TimeTableRow.TimeTableRowType type) {
        return new StopKey(trainNumber, stationShortCode, type);
    }

    public record StopKey(long trainNumber, String discriminator, TimeTableRow.TimeTableRowType type) {
    }
}
