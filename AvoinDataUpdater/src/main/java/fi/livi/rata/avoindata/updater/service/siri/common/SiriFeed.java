package fi.livi.rata.avoindata.updater.service.siri.common;

import java.util.Locale;

/**
 * The SIRI feeds the updater produces.
 */
public enum SiriFeed {
    ET,
    VM,
    SX;

    /**
     * The feed name as written in the logs, for example {@code sx}.
     */
    public String logName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
