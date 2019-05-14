package fi.livi.rata.avoindata.updater.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TrainLockExecutor {
    @Autowired
    private SimpleTransactionManager simpleTransactionManager;

    private Logger log = LoggerFactory.getLogger(TrainLockExecutor.class);

    final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public <T> T executeInTransactionLock(Callable<T> callable) {
        ZonedDateTime submittedAt = ZonedDateTime.now();

        Callable<T> wrappedCallable = () -> {
            ZonedDateTime executionStartedAt = ZonedDateTime.now();
            T returnValue = simpleTransactionManager.executeInTransaction(callable);
            if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                log.info("Waited: {}, Executed: {}", Duration.between(submittedAt, executionStartedAt), Duration.between(executionStartedAt, ZonedDateTime.now()));
            }
            return returnValue;
        };

        final Future<T> future = executorService.submit(wrappedCallable);

        try {
            return future.get();
        } catch (Exception e) {
            log.error("Error executing callable in TrainLockExecutor", e);
            return null;
        }
    }

    public <T> T executeInLock(Callable<T> callable) {
        ZonedDateTime submittedAt = ZonedDateTime.now();

        Callable<T> wrappedCallable = () -> {
            ZonedDateTime executionStartedAt = ZonedDateTime.now();
            T returnValue = callable.call();
            if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                log.info("Waited: {}, Executed: {}", Duration.between(submittedAt, executionStartedAt), Duration.between(executionStartedAt, ZonedDateTime.now()));
            }
            return returnValue;
        };

        final Future<T> future = executorService.submit(wrappedCallable);

        try {
            return future.get();
        } catch (Exception e) {
            log.error("Error executing callable in TrainLockExecutor", e);
            return null;
        }
    }
}
