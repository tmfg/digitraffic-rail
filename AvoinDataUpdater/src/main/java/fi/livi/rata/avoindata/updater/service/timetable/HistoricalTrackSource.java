package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.gtfs.TimeTableRowService;

/**
 * Track a train was last seen to use, for stops the schedule and the coming days
 * leave without one. Only rows with an actual time count, so a stop the train
 * never made cannot donate a track.
 */
@Component
public class HistoricalTrackSource {

    private static final Logger log = LoggerFactory.getLogger(HistoricalTrackSource.class);

    private final TimeTableRowService timeTableRowService;

    @Value("${updater.netex.track-history-days:14}")
    private int historyDays;

    public HistoricalTrackSource(final TimeTableRowService timeTableRowService) {
        this.timeTableRowService = timeTableRowService;
    }

    /**
     * Reads one day at a time, most recent first, and stops as soon as every stop
     * asked about has an answer. A train that runs daily is answered by yesterday
     * alone, so the older days are only read for stops that have not run recently.
     */
    public Map<StopKey, String> resolve(final Set<StopKey> wanted) {
        final Map<StopKey, String> found = new HashMap<>();
        if (wanted.isEmpty()) {
            return found;
        }

        final LocalDate today = DateProvider.dateInHelsinki();
        int daysRead = 0;
        for (int back = 1; back <= historyDays && found.size() < wanted.size(); back++) {
            daysRead++;
            for (final SimpleTimeTableRow row : timeTableRowService.getDay(today.minusDays(back))) {
                if (StringUtils.isBlank(row.commercialTrack) || row.actualTime == null) {
                    continue;
                }
                offer(found, wanted, stopKey(row), row.commercialTrack);
                offer(found, wanted, stationKey(row), row.commercialTrack);
            }
        }

        log.info("method=resolve wanted={} resolved={} daysRead={}", wanted.size(), found.size(), daysRead);
        return found;
    }

    private static void offer(final Map<StopKey, String> found, final Set<StopKey> wanted, final StopKey key,
            final String track) {
        if (wanted.contains(key)) {
            found.putIfAbsent(key, track);
        }
    }

    private static StopKey stopKey(final SimpleTimeTableRow row) {
        return new StopKey(row.getTrainNumber(), String.valueOf(row.getAttapId()), row.type);
    }

    private static StopKey stationKey(final SimpleTimeTableRow row) {
        return new StopKey(row.getTrainNumber(), row.stationShortCode, row.type);
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
