package fi.livi.rata.avoindata.updater.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static final int RETRY_PERIOD_MS = 10000;
    public static final int DEFAULT_MAX_ATTEMPTS = 2;
    private final int maxAttempts;

    public InitializerRetryTemplate() {
        this(DEFAULT_MAX_ATTEMPTS);
    }

    public InitializerRetryTemplate(final int maxAttempts) {
        // Retry maxAttempts times with a fixed period
        final RetryPolicy retryPolicy = new MaxAttemptsRetryPolicy(maxAttempts);
        setRetryPolicy(retryPolicy);
        final FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(RETRY_PERIOD_MS);
        setBackOffPolicy(backOffPolicy);

        registerListener(new LoggingRetryListener());
        this.maxAttempts = maxAttempts;
    }

    private class LoggingRetryListener implements RetryListener {

        @Override
        public <T, E extends Throwable> void onError(final RetryContext context, final RetryCallback<T, E> callback,
                final Throwable throwable) {

            log.error(
                    "Error during initialization retryCount={} of {} errorMessage: {}. Retrying in {} ms",
                    context.getRetryCount(), maxAttempts, throwable.getMessage(), RETRY_PERIOD_MS, throwable);
        }
    }
}
