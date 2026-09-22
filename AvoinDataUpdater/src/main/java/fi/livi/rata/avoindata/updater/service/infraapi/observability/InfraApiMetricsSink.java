package fi.livi.rata.avoindata.updater.service.infraapi.observability;

import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;

/**
 * Receives Infra API transport facts observed by the WebClient filter. Implemented by whatever
 * per-run accumulator is currently bound; unbound requests are simply not measured.
 */
public interface InfraApiMetricsSink {

    /** Used when no GTFS run is bound, so shared callers go unmeasured rather than failing. */
    InfraApiMetricsSink DISCARD = new InfraApiMetricsSink() {
        @Override
        public void recordUpstreamResponse(final InfraApiDataset dataset, final int statusCode, final long latencyMs,
                                           final long responseSizeBytes) {
        }

        @Override
        public void recordUpstreamTransportError(final InfraApiDataset dataset, final long latencyMs,
                                                 final Throwable error) {
        }

        @Override
        public void recordUpstreamRetry(final InfraApiDataset dataset) {
        }
    };

    void recordUpstreamResponse(InfraApiDataset dataset, int statusCode, long latencyMs, long responseSizeBytes);

    void recordUpstreamTransportError(InfraApiDataset dataset, long latencyMs, Throwable error);

    void recordUpstreamRetry(InfraApiDataset dataset);
}
