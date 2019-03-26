package fi.livi.rata.avoindata.updater.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;

@Service
public class TrainLockExecutor {
    private Logger log = LoggerFactory.getLogger(TrainLockExecutor.class);

    public <T> T executeInLock(Callable<T> callable) {
        ZonedDateTime submittedAt = ZonedDateTime.now();

        try {
            Pair<ZonedDateTime, T> returnPair = limitCall(callable);
            ZonedDateTime executionStartedAt = returnPair.getFirst();

            if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                log.info("Waited: {}, Executed: {}",
                        Duration.between(submittedAt, executionStartedAt),
                        Duration.between(executionStartedAt, ZonedDateTime.now()));
            }
            return returnPair.getSecond();
        } catch (Exception e) {
            log.error("Error executing callable in TrainLockExecutor", e);
            return null;
        }
    }

    private synchronized <T> Pair<ZonedDateTime, T> limitCall(Callable<T> callable) throws Exception {
        ZonedDateTime executionStartedAt = ZonedDateTime.now();

        return Pair.of(executionStartedAt, callable.call());
    }
}
