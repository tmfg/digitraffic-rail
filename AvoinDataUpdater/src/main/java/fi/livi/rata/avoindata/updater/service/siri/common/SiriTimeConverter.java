package fi.livi.rata.avoindata.updater.service.siri.common;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converts live timestamps to Nordic SIRI local-Helsinki ISO-8601 format.
 */
public class SiriTimeConverter {

    static final ZoneId HELSINKI_ZONE = ZoneId.of("Europe/Helsinki");

    public String toSiriDateTime(final ZonedDateTime instant) {
        if (instant == null) {
            return null;
        }
        return instant.withZoneSameInstant(HELSINKI_ZONE)
                .toLocalDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public String toSiriDuration(final Duration duration) {
        if (duration == null) {
            return null;
        }
        return duration.toString();
    }
}
