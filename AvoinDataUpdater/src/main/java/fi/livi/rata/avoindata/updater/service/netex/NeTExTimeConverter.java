package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Converts schedule times (UTC Duration from midnight) to NeTEx Nordic local time strings.
 * NeTEx Nordic uses Europe/Helsinki local time with >24:00:00 notation for past-midnight stops.
 */
public class NeTExTimeConverter {

    public static final ZoneId HELSINKI_ZONE = ZoneId.of("Europe/Helsinki");

    /**
     * Converts a UTC Duration timestamp to NeTEx local time string.
     *
     * @param utcTimestamp the time as Duration from midnight UTC (as stored in ScheduleRowPart.timestamp)
     * @param firstDepartureUtc the first stop's departure time as Duration from midnight UTC
     * @return time string in HH:mm:ss format, using >24:00:00 for past-midnight
     */
    public String toNeTExTime(final Duration utcTimestamp, final Duration firstDepartureUtc) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Converts a UTC Duration timestamp to local Helsinki time, accounting for DST.
     *
     * @param utcTimestamp the time as Duration from midnight UTC
     * @param referenceDate the operating date (needed to determine DST offset)
     * @return the local time in Helsinki
     */
    public LocalTime toHelsinkiLocalTime(final Duration utcTimestamp, final java.time.LocalDate referenceDate) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Formats a local time as NeTEx time string, applying >24:00:00 if past midnight.
     *
     * @param localTime the local time of this stop
     * @param firstDepartureLocalTime the local time of the first stop's departure
     * @return formatted time string (e.g., "05:30:00" or "25:30:00")
     */
    public String formatNeTExTime(final LocalTime localTime, final LocalTime firstDepartureLocalTime) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
