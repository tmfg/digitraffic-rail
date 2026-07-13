package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

/**
 * Converts schedule times (UTC Duration from midnight) to NeTEx Nordic local
 * time strings.
 * NeTEx Nordic uses Europe/Helsinki local time with >24:00:00 notation for
 * past-midnight stops.
 */
@Service
public class NeTExTimeConverter {

    public static final ZoneId HELSINKI_ZONE = ZoneId.of("Europe/Helsinki");

    /**
     * Converts a UTC Duration timestamp to NeTEx local time string.
     */
    public String toNeTExTime(final Duration utcTimestamp, final Duration firstDepartureUtc) {
        // Both are on the same reference date; use a fixed date for conversion
        final LocalDate refDate = LocalDate.of(2026, 1, 1);
        final LocalTime localTime = toHelsinkiLocalTime(utcTimestamp, refDate);
        final LocalTime firstLocal = toHelsinkiLocalTime(firstDepartureUtc, refDate);
        return formatNeTExTime(localTime, firstLocal);
    }

    /**
     * Converts a UTC Duration timestamp to local Helsinki time, accounting for DST.
     */
    public LocalTime toHelsinkiLocalTime(final Duration utcTimestamp, final LocalDate referenceDate) {
        final long totalSeconds = utcTimestamp.getSeconds();
        final int hours = (int) (totalSeconds / 3600);
        final int minutes = (int) ((totalSeconds % 3600) / 60);
        final int seconds = (int) (totalSeconds % 60);

        final ZonedDateTime utcDateTime = ZonedDateTime.of(referenceDate, LocalTime.of(hours % 24, minutes, seconds),
                ZoneOffset.UTC);
        final ZonedDateTime helsinkiDateTime = utcDateTime.withZoneSameInstant(HELSINKI_ZONE);
        return helsinkiDateTime.toLocalTime();
    }

    /**
     * Formats a local time as NeTEx time string, applying >24:00:00 if past
     * midnight.
     */
    public String formatNeTExTime(final LocalTime localTime, final LocalTime firstDepartureLocalTime) {
        if (localTime.equals(LocalTime.MIDNIGHT) && !firstDepartureLocalTime.equals(LocalTime.MIDNIGHT)) {
            // Midnight exactly, and first departure was before midnight → 24:00:00
            if (firstDepartureLocalTime.getHour() >= 12) {
                return "24:00:00";
            }
        }

        if (localTime.isBefore(firstDepartureLocalTime) && firstDepartureLocalTime.getHour() >= 12) {
            // Past midnight rollover: add 24 hours
            final int hours = localTime.getHour() + 24;
            return String.format("%02d:%02d:%02d", hours, localTime.getMinute(), localTime.getSecond());
        }

        return String.format("%02d:%02d:%02d", localTime.getHour(), localTime.getMinute(), localTime.getSecond());
    }
}
