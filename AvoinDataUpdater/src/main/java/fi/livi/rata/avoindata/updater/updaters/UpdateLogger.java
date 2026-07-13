package fi.livi.rata.avoindata.updater.updaters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class UpdateLogger {
    private final static Logger log = LoggerFactory.getLogger(UpdateLogger.class);

    private UpdateLogger() {}

    public static void logUpdate(final long tookMs, final String prefix, final long count) {
        log.info(
                "method=logUpdate Updated data for count={} prefix={} in tookMs={}",
                count, prefix, tookMs);
    }

    public static void logUpdate(final long tookMs, final String prefix, final long count, final long middleMs) {
        log.info(
                "method=logUpdate Updated data for count={} prefix={} in tookMs={} fetchTookMs={}",
                count, prefix, tookMs, middleMs);
    }

    public static void logUpdate(final long latestVersion, final long tookMs, final long count, final long newVersion,
                             final String prefix,
                             final long middleMs) {
        log.info(
                "method=logUpdate Updated data for count={} prefix={} in tookMs={} ms total ( json retrieve fetchTookMs={} ms, oldVersion={} newVersion={} versionDiff={} )",
                count, prefix,
                tookMs, middleMs, latestVersion, newVersion,
                (newVersion - latestVersion));
    }

}
