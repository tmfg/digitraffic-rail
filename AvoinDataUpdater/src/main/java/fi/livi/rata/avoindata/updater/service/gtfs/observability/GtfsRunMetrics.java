package fi.livi.rata.avoindata.updater.service.gtfs.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;

import fi.livi.rata.avoindata.updater.service.gtfs.NoGeometryReason;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMapResult;
import fi.livi.rata.avoindata.updater.service.infraapi.observability.InfraApiMetricsSink;
import fi.livi.rata.avoindata.updater.service.infraapi.observability.InfraApiSource;

/**
 * Accumulates the values emitted as the wide event of one GTFS run. Records only.
 */
public class GtfsRunMetrics implements InfraApiMetricsSink, RouteMetricsSink, ShapeMetricsSink, FeedMetricsSink {
    static final int HEARTBEAT_INTERVAL_SHAPES = 250;
    static final int MAX_ROUTE_FAILURE_SAMPLES = 20;
    private static final int TOP_DUMMY_STATIONS = 10;
    private static final String NO_FEED = "unknown";

    private final StopWatch runTimer = StopWatch.createStarted();
    private final Set<String> sampledFailureIdentities = new LinkedHashSet<>();
    private final List<Map<String, Object>> failureSamples = new ArrayList<>();
    private final Map<NoGeometryReason, Integer> dummyReasons = new EnumMap<>(NoGeometryReason.class);
    private final Map<String, Integer> dummyStations = new LinkedHashMap<>();
    private final Set<String> attemptedFeedNames = new LinkedHashSet<>();
    private final Set<String> publishedFeedNames = new LinkedHashSet<>();
    private final Set<String> failedFeedNames = new LinkedHashSet<>();
    private final Set<String> degradedFeedNames = new LinkedHashSet<>();
    private final Map<InfraApiDataset, UpstreamMetrics> upstream = new EnumMap<>(InfraApiDataset.class);
    private StopWatch feedTimer = StopWatch.createStarted();
    private String currentFeedName = NO_FEED;
    private long suppressedFailures;
    private int routeLookups;
    private int segmentsTotal;
    private int realSegments;
    private int dummySegments;
    private int totalShapes;
    private int realShapes;
    private int stationCount;
    private int stationPartCount;
    private String nodeCacheState = "";
    private long nodeCacheAgeMs;
    private String errorType;

    /**
     * @return the diagnostic event for this failure while the per-run cap allows it, otherwise
     *         empty. Repeats of an identity already seen are never re-emitted.
     */
    public Optional<Map<String, Object>> recordRouteFailure(final String segment, final String urlPath,
                                                            final int statusCode, final String errorType) {
        final String identity = segment + "|" + statusCode + "|" + errorType;
        if (!sampledFailureIdentities.add(identity)) {
            return Optional.empty();
        }
        if (failureSamples.size() >= MAX_ROUTE_FAILURE_SAMPLES) {
            suppressedFailures++;
            return Optional.empty();
        }
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("operation", "resolveGtfsRouteSegment");
        event.put("outcome", "error");
        InfraApiSource.addTo(event);
        event.put("rail.gtfs.feed.name", currentFeedName);
        event.put("rail.gtfs.segment", segment);
        event.put("url.path", urlPath);
        event.put("http.response.status_code", statusCode);
        event.put("error.type", errorType);
        failureSamples.add(event);
        return Optional.of(event);
    }

    public void recordDummySegment(final String stationCode) {
        segmentsTotal++;
        dummySegments++;
        if (stationCode != null) {
            dummyStations.merge(stationCode, 1, Integer::sum);
        }
    }

    public void recordNoGeometryReason(final NoGeometryReason reason) {
        dummyReasons.merge(reason, 1, Integer::sum);
    }

    @Override
    public void recordRouteOutcome(final RouteOutcome outcome) {
        switch (outcome) {
            case RESOLVED -> {
            }
            case EMPTY_GEOMETRY -> recordNoGeometryReason(NoGeometryReason.ROUTE_EMPTY_GEOMETRY);
            case NO_PATH -> recordNoGeometryReason(NoGeometryReason.NO_DIJKSTRA_PATH);
        }
    }

    public void recordRealSegment() {
        segmentsTotal++;
        realSegments++;
    }

    public void recordFeedAttempt(final String feedName) {
        feedTimer = StopWatch.createStarted();
        currentFeedName = feedName;
        attemptedFeedNames.add(feedName);
    }

    public void recordFeedPublished(final String feedName) {
        publishedFeedNames.add(feedName);
    }

    public void recordFeedFailed(final String feedName) {
        failedFeedNames.add(feedName);
    }

    public void recordFeedDegraded() {
        degradedFeedNames.add(currentFeedName);
    }

    @Override
    public void recordUpstreamResponse(final InfraApiDataset dataset, final int statusCode, final long latencyMs,
                                       final long responseSizeBytes) {
        dataset(dataset).recordResponse(statusCode, latencyMs, responseSizeBytes);
    }

    @Override
    public void recordUpstreamTransportError(final InfraApiDataset dataset, final long latencyMs, final Throwable error) {
        dataset(dataset).recordTransportError(latencyMs);
    }

    @Override
    public void recordUpstreamRetry(final InfraApiDataset dataset) {
        dataset(dataset).retries++;
    }

    /**
     * A route lookup is one call into the cached route service. The method body only runs on a cache
     * miss, so misses are counted from the requests the WebClient filter actually observed.
     */
    public void recordRouteLookup() {
        routeLookups++;
    }

    public void recordShapes(final int total, final int real) {
        totalShapes += total;
        realShapes += real;
    }

    public void recordNodeMap(final InfraApiMapResult<?> nodeMap) {
        stationCount = nodeMap.countOf(InfraApiDataset.RAUTATIELIIKENNEPAIKAT);
        stationPartCount = nodeMap.countOf(InfraApiDataset.LIIKENNEPAIKANOSAT);
        nodeCacheState = nodeMap.cacheState().name().toLowerCase(Locale.ROOT);
        nodeCacheAgeMs = nodeMap.age(Instant.now()).toMillis();
    }

    public void markError(final Throwable throwable) {
        if (errorType == null) {
            errorType = throwable.getClass().getSimpleName();
        }
    }

    /**
     * @return the heartbeat event when this shape lands on a heartbeat boundary, otherwise empty.
     */
    public Optional<Map<String, Object>> recordShapeProcessed(final int processedShapes,
                                                              final int totalShapesInFeed) {
        if (processedShapes <= 0 || processedShapes % HEARTBEAT_INTERVAL_SHAPES != 0) {
            return Optional.empty();
        }
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("operation", "generateGtfsFeed");
        event.put("outcome", "in_progress");
        event.put("rail.gtfs.feed.name", currentFeedName);
        event.put("rail.gtfs.shapes.processed", processedShapes);
        event.put("rail.gtfs.shapes.total", totalShapesInFeed);
        event.put("rail.gtfs.segments.total", segmentsTotal);
        event.put("rail.gtfs.segments.real", realSegments);
        event.put("rail.gtfs.segments.dummy", dummySegments);
        for (final NoGeometryReason reason : List.of(NoGeometryReason.NO_START_NODE, NoGeometryReason.NO_END_NODE,
                NoGeometryReason.ROUTE_HTTP_ERROR)) {
            event.put("rail.gtfs.segments.dummy.reason." + reason.attribute(), dummyReasons.getOrDefault(reason, 0));
        }
        event.put("duration_ms", feedTimer.getDuration().toMillis());
        return Optional.of(event);
    }

    public List<Map<String, Object>> routeFailureSamples() {
        return List.copyOf(failureSamples);
    }

    public long suppressedRouteFailures() {
        return suppressedFailures;
    }

    public GtfsOutcome outcome() {
        if (!failedFeedNames.isEmpty()) {
            return publishedFeedNames.isEmpty() ? GtfsOutcome.ERROR : GtfsOutcome.PARTIAL;
        }
        if (errorType != null) {
            return GtfsOutcome.ERROR;
        }
        return dummySegments > 0 || !degradedFeedNames.isEmpty() ? GtfsOutcome.DEGRADED : GtfsOutcome.SUCCESS;
    }

    public Map<String, Object> finalEvent() {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("operation", "generateGtfs");
        event.put("rail.entity.type", "gtfs_feed");
        event.put("outcome", outcome().attribute());
        event.put("duration_ms", runTimer.getDuration().toMillis());
        event.put("error.type", errorType == null ? "" : errorType);
        InfraApiSource.addTo(event);
        event.put("rail.gtfs.feeds.attempted", attemptedFeedNames.size());
        event.put("rail.gtfs.feeds.published", publishedFeedNames.size());
        event.put("rail.gtfs.feeds.failed", failedFeedNames.size());
        event.put("rail.gtfs.feeds.degraded", csv(degradedFeedNames));
        event.put("rail.gtfs.nodes.rautatieliikennepaikat.count", stationCount);
        event.put("rail.gtfs.nodes.liikennepaikanosat.count", stationPartCount);
        event.put("rail.gtfs.nodes.cache.state", nodeCacheState);
        event.put("rail.gtfs.nodes.cache.age_ms", nodeCacheAgeMs);
        event.put("rail.gtfs.segments.total", segmentsTotal);
        event.put("rail.gtfs.segments.real", realSegments);
        event.put("rail.gtfs.segments.dummy", dummySegments);
        for (final NoGeometryReason reason : NoGeometryReason.values()) {
            event.put("rail.gtfs.segments.dummy.reason." + reason.attribute(), dummyReasons.getOrDefault(reason, 0));
        }
        event.put("rail.gtfs.segments.dummy.stations.top", topDummyStations());
        event.put("rail.gtfs.shapes.total", totalShapes);
        event.put("rail.gtfs.shapes.real", realShapes);
        // Samples are emitted as their own events; a nested list cannot survive key=value rendering.
        event.put("rail.gtfs.route_failures.sampled", failureSamples.size());
        event.put("rail.gtfs.route_failures.suppressed", suppressedFailures);

        for (final String feedName : attemptedFeedNames) {
            final String prefix = "rail.gtfs.feed." + feedName + ".";
            event.put(prefix + "published", publishedFeedNames.contains(feedName));
            event.put(prefix + "failed", failedFeedNames.contains(feedName));
            event.put(prefix + "degraded", degradedFeedNames.contains(feedName));
        }
        for (final InfraApiDataset dataset : InfraApiDataset.values()) {
            dataset(dataset).addTo(event, "rail.upstream.infra_api." + dataset.metricKey() + ".");
        }

        final String reititPrefix = "rail.upstream.infra_api." + InfraApiDataset.REITIT.metricKey() + ".";
        final int misses = dataset(InfraApiDataset.REITIT).firstAttempts();
        event.put(reititPrefix + "cache.misses", misses);
        event.put(reititPrefix + "cache.hits", Math.max(0, routeLookups - misses));
        return event;
    }

    private UpstreamMetrics dataset(final InfraApiDataset dataset) {
        return upstream.computeIfAbsent(dataset, ignored -> new UpstreamMetrics());
    }

    private String topDummyStations() {
        return dummyStations.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_DUMMY_STATIONS)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
    }

    private static String csv(final Set<String> values) {
        return String.join(",", new TreeSet<>(values));
    }

    private static final class UpstreamMetrics {
        private final List<Long> latencies = new ArrayList<>();
        private int requests;
        private int failed;
        private int status2xx;
        private int status4xx;
        private int status5xx;
        private int transportErrors;
        private long responseSize;
        private int retries;

        private void recordResponse(final int status, final long latencyMs, final long size) {
            requests++;
            latencies.add(latencyMs);
            responseSize += size;
            if (status >= 200 && status < 300) {
                status2xx++;
            } else if (status >= 400 && status < 500) {
                status4xx++;
                failed++;
            } else if (status >= 500) {
                status5xx++;
                failed++;
            }
        }

        private void recordTransportError(final long latencyMs) {
            requests++;
            failed++;
            transportErrors++;
            latencies.add(latencyMs);
        }

        private void addTo(final Map<String, Object> event, final String prefix) {
            final List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            event.put(prefix + "requests.total", requests);
            event.put(prefix + "requests.failed", failed);
            event.put(prefix + "requests.failure_ratio", requests == 0 ? 0.0 : (double) failed / requests);
            event.put(prefix + "status.2xx", status2xx);
            event.put(prefix + "status.4xx", status4xx);
            event.put(prefix + "status.5xx", status5xx);
            event.put(prefix + "status.transport_error", transportErrors);
            event.put(prefix + "latency_p50_ms", percentile(sorted, 0.50));
            event.put(prefix + "latency_p95_ms", percentile(sorted, 0.95));
            event.put(prefix + "latency_max_ms", sorted.isEmpty() ? 0L : sorted.getLast());
            event.put(prefix + "response_size_bytes.total", responseSize);
            event.put(prefix + "retries.total", retries);
        }

        /** Distinct route calls that reached the network, i.e. excluding retried attempts. */
        private int firstAttempts() {
            return requests - retries;
        }

        private static long percentile(final List<Long> values, final double percentile) {
            return values.isEmpty() ? 0L : values.get((int) Math.ceil(percentile * values.size()) - 1);
        }
    }
}
