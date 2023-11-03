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

    public <T> T executeInTransactionLock(final String context, final Callable<T> callable) {
        final ZonedDateTime submittedAt = ZonedDateTime.now();

        final Callable<T> wrappedCallable = () -> {
            log.info("Executing callable for " + context);

            final ZonedDateTime executionStartedAt = ZonedDateTime.now();
            final T returnValue = simpleTransactionManager.executeInTransaction(callable);
            if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                log.info("Waited: {}, Executed: {}, Context: {}",
                        Duration.between(submittedAt, executionStartedAt),
                        Duration.between(executionStartedAt, ZonedDateTime.now()),
                        context);
            }
            return returnValue;
        };

        final Future<T> future = submitCallable(context, wrappedCallable);

        try {
            return future.get();
        } catch (final Exception e) {
            log.error("Error executing callable in TrainLockExecutor, context: " + context, e);
            return null;
        }
    }

    public <T> T executeInLock(final String context, final Callable<T> callable) {
        final ZonedDateTime submittedAt = ZonedDateTime.now();

        final Callable<T> wrappedCallable = () -> {
            log.info("Executing callable for " + context);

            final ZonedDateTime executionStartedAt = ZonedDateTime.now();
            final T returnValue = callable.call();
            if (Duration.between(submittedAt, executionStartedAt).toMillis() > 10000) {
                log.info("Waited: {}, Executed: {}, Context: {}",
                        Duration.between(submittedAt, executionStartedAt),
                        Duration.between(executionStartedAt, ZonedDateTime.now()),
                        context);
            }
            return returnValue;
        };

        final Future<T> future = submitCallable(context, wrappedCallable);

        try {
            return future.get();
        } catch (final Exception e) {
            log.error("Error executing callable in TrainLockExecutor, context: " + context, e);
            return null;
        }
    }

    private <T> Future<T> submitCallable(final String context, final Callable<T> callable) {
        log.info("Submitting callable for " + context);

        return executorService.submit(callable);
    }
}
