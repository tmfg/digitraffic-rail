package fi.livi.rata.avoindata.updater.service.recentlyseen;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class RecentlySeenTrainLocationFilter extends AbstractRecentlySeenEntityFilter<TrainLocation, String> {
    public static final int TIMESTAMP_RECENT_TRESHOLD_MINUTES = 25;
    @Autowired
    private DateProvider dp;

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
        return timestamp.isBefore(dp.nowInHelsinki().minusMinutes(TIMESTAMP_RECENT_TRESHOLD_MINUTES));
    }


    @Override
    public Logger getLogger() {
        return LoggerFactory.getLogger(RecentlySeenTrainLocationFilter.class);
    }
}
