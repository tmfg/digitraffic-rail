package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.Locale;

public enum GtfsOutcome {
    SUCCESS,
    /** Some feeds published, others failed. */
    PARTIAL,
    /** Every feed published, but from degraded input or with fallback geometry. */
    DEGRADED,
    ERROR;

    private final String attribute = name().toLowerCase(Locale.ROOT);

    public String attribute() {
        return attribute;
    }
}
