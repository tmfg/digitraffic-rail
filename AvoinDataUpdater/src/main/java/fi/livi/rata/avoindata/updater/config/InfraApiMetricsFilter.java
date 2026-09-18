package fi.livi.rata.avoindata.updater.config;

import java.time.Duration;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMetricsSink;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiRunContext;
import reactor.core.publisher.Mono;

/**
 * Collects Infra API transport metrics at the one place that can see status, latency and size for
 * every request, instead of threading a collector through the call sites that issue them.
 */
public class InfraApiMetricsFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
        // Resolved at subscribe time, while still on the thread that owns the run.
        final InfraApiMetricsSink sink = InfraApiRunContext.current();
        if (sink == null) {
            return next.exchange(request);
        }

        final InfraApiDataset dataset = InfraApiDataset.fromPath(request.url().getPath());
        final long startedAt = System.nanoTime();

        return next.exchange(request)
                .doOnNext(response -> sink.recordUpstreamResponse(dataset, response.statusCode().value(),
                        elapsedMs(startedAt), response.headers().contentLength().orElse(0L)))
                .doOnError(error -> sink.recordUpstreamTransportError(dataset, elapsedMs(startedAt), error));
    }

    private static long elapsedMs(final long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }
}
