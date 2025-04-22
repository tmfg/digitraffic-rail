package fi.livi.rata.avoindata.common.utils;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;

public abstract class TimingUtil {
    public static void log(final Logger log, final String method, final Runnable runnable) {
        final StopWatch stopWatch = StopWatch.createStarted();

        try {
            runnable.run();
        } finally {
            log.info("method={} tookMs={}", method, stopWatch.getDuration().toMillis());
        }
    }

}
