package fi.livi.rata.avoindata.updater.service.recentlyseen;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class RecentlySeenForecastFilter extends AbstractRecentlySeenEntityFilter<Forecast, String> {
    public static final int TIMESTAMP_RECENT_TRESHOLD_MINUTES = 60 * 24;
    @Autowired
    private DateProvider dp;

    @Override
    public ZonedDateTime getTimestamp(final Forecast entity) {
        return entity.forecastTime;
    }

    @Override
    public String getKey(final Forecast entity) {
        return String.format("%s_%s_%s_%s", entity.id, entity.source, entity.forecastTime, entity.lastModified);
    }

    @Override
    public boolean isTooOld(final ZonedDateTime timestamp) {
        if (timestamp == null) {
            return false;
        } else {
            return timestamp.isBefore(dp.nowInHelsinki().minusMinutes(TIMESTAMP_RECENT_TRESHOLD_MINUTES));
        }
    }


    @Override
    public Logger getLogger() {
        return LoggerFactory.getLogger(RecentlySeenForecastFilter.class);
    }
}
