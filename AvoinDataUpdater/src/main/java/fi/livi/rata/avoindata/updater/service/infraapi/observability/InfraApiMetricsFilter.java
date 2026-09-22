package fi.livi.rata.avoindata.updater.service.infraapi.observability;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.time.StopWatch;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import fi.livi.rata.avoindata.updater.service.gtfs.observability.GtfsRunScope;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;
import reactor.core.publisher.Mono;

/**
 * Collects Infra API transport metrics at the one place that can see status, latency and size for
 * every request, instead of threading a collector through the call sites that issue them.
 */
public class InfraApiMetricsFilter implements ExchangeFilterFunction {

    private static final String INFRA_API_PATH = "/infra-api";

    @Override
    public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
        final String path = request.url().getPath();
        // The filter is installed on the shared WebClient and inherited by derived clients, so
        // non-Infra traffic during a run would otherwise be attributed to Infra API.
        if (path == null || !path.contains(INFRA_API_PATH)) {
            return next.exchange(request);
        }

        // Resolved at subscribe time, while still on the thread that owns the run.
        final InfraApiMetricsSink sink = GtfsRunScope.infraApiMetrics();

        final InfraApiDataset dataset = InfraApiDataset.fromPath(path);
        final StopWatch stopWatch = StopWatch.createStarted();

        return next.exchange(request)
                // Recorded when the body completes, not when headers arrive: latency has to include
                // body transfer, and chunked responses carry no Content-Length to count.
                .map(response -> response.mutate()
                        .body(body -> {
                            final AtomicLong bytes = new AtomicLong();
                            return body
                                    .doOnNext(buffer -> bytes.addAndGet(buffer.readableByteCount()))
                                    .doOnComplete(() -> sink.recordUpstreamResponse(dataset,
                                            response.statusCode().value(), elapsedMs(stopWatch), bytes.get()))
                                    .doOnError(error -> sink.recordUpstreamTransportError(dataset,
                                            elapsedMs(stopWatch), error));
                        })
                        .build())
                .doOnError(error -> sink.recordUpstreamTransportError(dataset, elapsedMs(stopWatch), error));
    }

    private static long elapsedMs(final StopWatch stopWatch) {
        return stopWatch.getDuration().toMillis();
    }
}
