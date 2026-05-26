package fi.livi.rata.avoindata.updater.service.recentlyseen;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class RecentlySeenRoutesetFilter extends AbstractRecentlySeenEntityFilter<Routeset, Long> {
    private static final Logger log = LoggerFactory.getLogger(RecentlySeenRoutesetFilter.class);
    public static final int TIMESTAMP_RECENT_TRESHOLD_MINUTES = 60 * 24;

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
        if (timestamp == null) {
            return true; // we might receive invalid routesets with null messageTime
                         // they should be filtered out before but this prevents NPE if not
        }
        return timestamp.isBefore(DateProvider.nowInHelsinki().minusMinutes(TIMESTAMP_RECENT_TRESHOLD_MINUTES));
    }

    @Override
    public Logger getLogger() {
        return log;
    }
}
