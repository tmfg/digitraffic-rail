package fi.livi.rata.avoindata.common.utils;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;

public abstract class TimingUtil {
    public static void log(final Logger log, final String name, final Runnable runnable) {
        final ZonedDateTime start = ZonedDateTime.now();

        try {
            runnable.run();
        } finally {
            log.info("{} took {} ms", name, Duration.between(start, ZonedDateTime.now()).toMillis());
        }
    }

}
