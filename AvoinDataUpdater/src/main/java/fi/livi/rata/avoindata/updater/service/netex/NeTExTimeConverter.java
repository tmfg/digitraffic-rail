package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Duration;
import java.time.LocalTime;

import org.springframework.stereotype.Service;

/**
 * Formats schedule times as NeTEx Nordic time strings.
 *
 * Schedule times arrive as a duration from midnight and are already Helsinki
 * local wall-clock, the
 * same value the /trains API and the GTFS package publish, so nothing is
 * converted here. Only the
 * >24:00:00 notation for stops past midnight is applied.
 */
@Service
public class NeTExTimeConverter {

    public String toNeTExTime(final Duration timestamp, final Duration firstDeparture) {
        return formatNeTExTime(toLocalTime(timestamp), toLocalTime(firstDeparture));
    }

    public LocalTime toLocalTime(final Duration timestamp) {
        final long totalSeconds = timestamp.getSeconds();
        return LocalTime.of((int) (totalSeconds / 3600) % 24,
                (int) ((totalSeconds % 3600) / 60),
                (int) (totalSeconds % 60));
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
