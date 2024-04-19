package fi.livi.rata.avoindata.updater.config;

import org.slf4j.Logger;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class InitializerRetryTemplate extends RetryTemplate {

    public static final int RETRY_PERIOD = 10000;
    public static final int DEFAULT_MAX_ATTEMPTS = 1;
    private Logger initializerLogger;

    public InitializerRetryTemplate() {
        this(DEFAULT_MAX_ATTEMPTS);
    }

    public InitializerRetryTemplate(final int maxAttempts) {
        // Retry once with a fixed period
        final RetryPolicy retryPolicy = new MaxAttemptsRetryPolicy(maxAttempts);
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
        public <T, E extends Throwable> void onError(final RetryContext context, final RetryCallback<T, E> callback,
                final Throwable throwable) {
            if (initializerLogger != null) {
                initializerLogger.error(
                        "Error during initialization retryCount={} errorMessage: {}. Retrying in {} ms",
                        context.getRetryCount(), throwable.getMessage(), RETRY_PERIOD , throwable);
            }
        }
    }
}
