package fi.livi.rata.avoindata.updater.service.recentlyseen;

import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class RecentlySeenRoutesetFilter extends AbstractRecentlySeenEntityFilter<Routeset, Long> {
    public static final int TIMESTAMP_RECENT_TRESHOLD_MINUTES = 60 * 4;
    @Autowired
    private DateProvider dp;

    @Override
    public ZonedDateTime getTimestamp(final Routeset entity) {
        return entity.messageTime;
    }

    @Override
    public Long getKey(final Routeset entity) {
        return entity.id;
    }

    @Override
    public boolean isTooOld(final ZonedDateTime timestamp) {
        return timestamp.isBefore(dp.nowInHelsinki().minusMinutes(TIMESTAMP_RECENT_TRESHOLD_MINUTES));
    }


    @Override
    public Logger getLogger() {
        return LoggerFactory.getLogger(RecentlySeenRoutesetFilter.class);
    }
}
