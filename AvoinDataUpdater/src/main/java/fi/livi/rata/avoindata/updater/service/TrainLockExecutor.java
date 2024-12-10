package fi.livi.rata.avoindata.updater.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrainLockExecutor {
    private static final int LIMIT_MILLIS_WAITING = 10000;
    private static final int LIMIT_MILLIS_EXECUTING = 20000;
    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    @Autowired
    private SimpleTransactionManager simpleTransactionManager;
    private final Logger log = LoggerFactory.getLogger(TrainLockExecutor.class);

    public <T> T executeInTransactionLock(final String context, final Callable<T> callable) {
        return execute(context, true, callable);
    }

    public <T> T executeInLock(final String context, final Callable<T> callable) {
        return execute(context, false, callable);
    }

    private <T> T execute(final String context, final boolean inTransaction, final Callable<T> callable) {
        final ZonedDateTime submittedAt = ZonedDateTime.now();

        final Callable<T> wrappedCallable = () -> {
            log.debug("method=execute Executing callable for " + context);

            final ZonedDateTime executionStartedAt = ZonedDateTime.now();

            final T returnValue;
            if (inTransaction) {
                returnValue = simpleTransactionManager.executeInTransaction(callable);
            } else {
                returnValue = callable.call();
            }

            if (shouldLog(submittedAt, executionStartedAt)) {
                log.info("method=execute Waited: {}, Executed: {}, Context: {}",
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
            log.error("method=execute Error executing callable in TrainLockExecutor, context: " + context, e);
            return null;
        }
    }

    private boolean shouldLog(final ZonedDateTime submittedAt, final ZonedDateTime executionStartedAt) {
        return Duration.between(submittedAt, executionStartedAt).toMillis() > LIMIT_MILLIS_WAITING || Duration.between(executionStartedAt,
            ZonedDateTime.now()).toMillis() > LIMIT_MILLIS_EXECUTING;
    }

    private <T> Future<T> submitCallable(final String context, final Callable<T> callable) {
        log.debug("method=submitCallable Submitting callable for " + context);

        return executorService.submit(callable);
    }
}
