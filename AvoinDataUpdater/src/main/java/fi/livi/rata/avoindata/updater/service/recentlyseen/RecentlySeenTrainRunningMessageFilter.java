package fi.livi.rata.avoindata.updater.service.recentlyseen;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class RecentlySeenTrainRunningMessageFilter extends AbstractRecentlySeenEntityFilter<TrainRunningMessage, Long> {
    public static final int TIMESTAMP_RECENT_TRESHOLD_MINUTES = 60 * 4;

    @Override
    public ZonedDateTime getTimestamp(final TrainRunningMessage entity) {
        return entity.timestamp;
    }

    @Override
    public Long getKey(final TrainRunningMessage entity) {
        return entity.id;
    }

    @Override
    public boolean isTooOld(final ZonedDateTime timestamp) {
        return timestamp.isBefore(DateProvider.nowInHelsinki().minusMinutes(TIMESTAMP_RECENT_TRESHOLD_MINUTES));
    }


    @Override
    public Logger getLogger() {
        return LoggerFactory.getLogger(RecentlySeenTrainRunningMessageFilter.class);
    }
}
