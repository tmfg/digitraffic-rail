package fi.livi.rata.avoindata.updater.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMetricsSink;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiRunContext;

/**
 * Retry policy for Infra API reads. 5xx is retried because the failure mode being addressed is
 * {@code 503 Too many concurrent database operations}; 500 and the other 4xx statuses are
 * deterministic and are not retried.
 */
public final class InfraApiRetry {
    private InfraApiRetry() {
    }

    public static RetryTemplate create() {
        return create(2_000, 60_000);
    }

    public static RetryTemplate create(final long initialIntervalMs, final long maxIntervalMs) {
        final Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(WebClientResponseException.ServiceUnavailable.class, true);
        retryable.put(WebClientResponseException.GatewayTimeout.class, true);
        retryable.put(WebClientResponseException.BadGateway.class, true);
        retryable.put(WebClientResponseException.TooManyRequests.class, true);
        retryable.put(WebClientRequestException.class, true);

        final RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new SimpleRetryPolicy(5, retryable, false));
        final ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(maxIntervalMs);
        template.setBackOffPolicy(backOff);
        template.registerListener(new RetryCountingListener());
        return template;
    }

    /** The WebClient filter sees every attempt as a request; only the policy knows it was a retry. */
    private static final class RetryCountingListener implements RetryListener {
        @Override
        public <T, E extends Throwable> void onError(final RetryContext context, final RetryCallback<T, E> callback,
                                                     final Throwable throwable) {
            if (context.getRetryCount() <= 1) {
                return;
            }
            final InfraApiMetricsSink sink = InfraApiRunContext.current();
            if (sink != null) {
                sink.recordUpstreamRetry(datasetOf(throwable));
            }
        }

        private static InfraApiDataset datasetOf(final Throwable throwable) {
            if (throwable instanceof WebClientResponseException responseException && responseException.getRequest() != null) {
                return InfraApiDataset.fromPath(responseException.getRequest().getURI().getPath());
            }
            if (throwable instanceof WebClientRequestException requestException) {
                return InfraApiDataset.fromPath(requestException.getUri().getPath());
            }
            return InfraApiDataset.OTHER;
        }
    }
}