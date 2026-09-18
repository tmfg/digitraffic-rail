package fi.livi.rata.avoindata.updater.service.infraapi;

/**
 * Receives Infra API transport facts observed by the WebClient filter. Implemented by whatever
 * per-run accumulator is currently bound; unbound requests are simply not measured.
 */
public interface InfraApiMetricsSink {

    void recordUpstreamResponse(InfraApiDataset dataset, int statusCode, long latencyMs, long responseSizeBytes);

    void recordUpstreamTransportError(InfraApiDataset dataset, long latencyMs, Throwable error);

    void recordUpstreamRetry(InfraApiDataset dataset);
}
