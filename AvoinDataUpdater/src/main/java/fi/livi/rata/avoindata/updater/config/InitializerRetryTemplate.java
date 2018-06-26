package fi.livi.rata.avoindata.updater.config;

import org.slf4j.Logger;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
public class InitializerRetryTemplate extends RetryTemplate {

    public static final int RETRY_PERIOD = 10000;
    private Logger initializerLogger;

    public InitializerRetryTemplate() {

        // Retry forever with a fixed period
        final RetryPolicy retryPolicy = new AlwaysRetryPolicy();
        setRetryPolicy(retryPolicy);
        final FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(RETRY_PERIOD);
        setBackOffPolicy(backOffPolicy);

        registerListener(new LoggingRetryListener());
    }

    public void setLogger(final Logger logger) {
        this.initializerLogger = logger;
    }

    private class LoggingRetryListener implements RetryListener {
        @Override
        public <T, E extends Throwable> boolean open(final RetryContext context, final RetryCallback<T, E> callback) {
            return true;
        }

        @Override
        public <T, E extends Throwable> void close(final RetryContext context, final RetryCallback<T, E> callback,
                final Throwable throwable) {
        }

        @Override
        public <T, E extends Throwable> void onError(final RetryContext context, final RetryCallback<T, E> callback,
                final Throwable throwable) {
            if (initializerLogger != null) {
                if (throwable instanceof ResourceAccessException) {
                    initializerLogger.error("Error during initialization: " + throwable.getMessage() + ". Retrying in " + RETRY_PERIOD + "ms");
                } else {
                    initializerLogger.error("Error during initialization", throwable);
                }
            }
        }
    }
}
