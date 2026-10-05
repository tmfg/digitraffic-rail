package fi.livi.rata.avoindata.updater.service.recentlyseen;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Filters out recently seen train locations.  If you're wondering how the threshold
 * can be so high, note that the key is (train number, departure_date, timestamp)!
 *
 * So this is meant to filter out duplicate train locations.
 */
@Component
public class RecentlySeenTrainLocationFilter extends AbstractRecentlySeenEntityFilter<TrainLocation, String> {
    public static final int TIMESTAMP_RECENT_THRESHOLD_MINUTES = 25;

    @Override
    public ZonedDateTime getTimestamp(final TrainLocation entity) {
        return entity.trainLocationId.timestamp;
    }

    @Override
    public String getKey(final TrainLocation entity) {
        return String.format("%s_%s_%s", entity.trainLocationId.trainNumber, entity.trainLocationId.departureDate,
                entity.trainLocationId.timestamp);
    }

    @Override
    public boolean isTooOld(final ZonedDateTime timestamp) {
        return timestamp.isBefore(DateProvider.nowInHelsinki().minusMinutes(TIMESTAMP_RECENT_THRESHOLD_MINUTES));
    }

    @Override
    public Logger getLogger() {
        return LoggerFactory.getLogger(RecentlySeenTrainLocationFilter.class);
    }
}
