package fi.livi.rata.avoindata.updater.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.retry.support.RetryTemplate;
import fi.livi.rata.avoindata.updater.config.InfraApiRetry;

import tools.jackson.databind.JsonNode;
import com.google.common.base.Strings;

import fi.livi.digitraffic.common.cache.ExpiringCache;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMapResult;

/**
 * infra-api version 0.4 or newer is needed!
 */
@Component
public class TrakediaLiikennepaikkaService {

    private final RetryTemplate retryTemplate = InfraApiRetry.create();

    @Autowired
    private WebClient webClient;

    private static final Logger logger = LoggerFactory.getLogger(TrakediaLiikennepaikkaService.class);

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Value("${updater.liikennepaikat.url}")
    private String liikennepaikatUrl;

    @Value("${updater.liikennepaikanosat.url}")
    private String liikennepaikanosatUrl;

    @Value("${updater.raideosuudet.url}")
    private String raideosuudetUrl;

    private final ExpiringCache<InfraApiMapResult<Double[]>> lpCache = new ExpiringCache<>(Duration.ofHours(12));
    private final ExpiringCache<InfraApiMapResult<JsonNode>> lpNodeCache = new ExpiringCache<>(Duration.ofHours(12));

    /**
     * A failed fetch is not cacheable, so without this every caller would re-run the full retry
     * sequence. Suppressing refreshes briefly keeps a degraded Infra API from stalling hot paths.
     */
    private static final Duration FAILURE_SUPPRESSION = Duration.ofMinutes(1);

    private final FailureWindow nodeMapFailures = new FailureWindow();
    private final FailureWindow coordinateMapFailures = new FailureWindow();

    public InfraApiMapResult<Double[]> getTrakediaLiikennepaikkas() {
        final AtomicBoolean refreshed = new AtomicBoolean();
        final InfraApiMapResult<Double[]> result = lpCache.get(() -> {
            refreshed.set(true);
            return loadMap(coordinateMapFailures, MapKind.COORDINATE, () -> {
                final var liikennepaikkaMap = fetchLiikennepaikkaMap(liikennepaikatUrl);
                final var liikennepaikkaOsaMap = fetchLiikennepaikkaMap(liikennepaikanosatUrl);
                final var raideosuusMap = fetchRaideosuusMap(raideosuudetUrl);

                // Counts are captured per source before merging, which destroys the split.
                final var sourceCounts = new EnumMap<InfraApiDataset, Integer>(InfraApiDataset.class);
                sourceCounts.put(InfraApiDataset.RAUTATIELIIKENNEPAIKAT, liikennepaikkaMap.size());
                sourceCounts.put(InfraApiDataset.LIIKENNEPAIKANOSAT, liikennepaikkaOsaMap.size());
                sourceCounts.put(InfraApiDataset.RAIDEOSUUDET, raideosuusMap.size());

                final var mergedMap = new HashMap<>(liikennepaikkaMap);
                mergedMap.putAll(liikennepaikkaOsaMap);
                mergedMap.putAll(raideosuusMap);
                return InfraApiMapResult.success(mergedMap, sourceCounts, Instant.now());
            });
        });
        return refreshed.get() ? result : result.asCacheHit();
    }

    private Double[] calculateCenterPoint(final JsonNode geometria) {
        final List<Coordinate> coordinates = new ArrayList<>();

        for(final JsonNode node : geometria.get(0)) {
            coordinates.add(new Coordinate(node.get(0).asDouble(), node.get(1).asDouble()));
        }

        final LineString lineString = geometryFactory.createLineString(coordinates.toArray(new Coordinate[]{}));
        final Point centroid = lineString.getCentroid();

        // too much precision, remove decimals
        final long x = (long)centroid.getX();
        final long y = (long)centroid.getY();
        return new Double[]{(double)x, (double)y};
    }

    private Map<String, Double[]> fetchRaideosuusMap(final String url) {
        final Map<String, Double[]> raideosuusMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return raideosuusMap;
        }

        logger.info("method=fetchRaideosuusMap Fetching Trakedia data from {}", url);

        final JsonNode jsonNode = retryTemplate.execute(context -> webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block());

        if (jsonNode == null) {
            throw new IllegalStateException("Infra-API returned null for " + url);
        }

        for (final JsonNode node : jsonNode) {
            final JsonNode geometria = node.get(0).get("geometria");
            final JsonNode lyhenne = node.get(0).get("lyhenne");
            raideosuusMap.put(lyhenne.asText().toUpperCase(), calculateCenterPoint(geometria));
        }

        return raideosuusMap;
    }

    public Map<String, Double[]> fetchLiikennepaikkaMap(final String url) {
        final Map<String, Double[]> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return liikennepaikkaMap;
        }

        logger.info("method=fetchLiikennepaikkaMap Fetching Trakedia data from {}", url);

        final JsonNode jsonNode = retryTemplate.execute(context -> webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block());

        if (jsonNode == null) {
            throw new IllegalStateException("Infra-API returned null for " + url);
        }

        for (final JsonNode node : jsonNode) {
            final JsonNode virallinenSijainti = node.get(0).get("virallinenSijainti");
            final JsonNode lyhenne = node.get(0).get("lyhenne");
            liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), new Double[]{virallinenSijainti.get(0).asDouble(), virallinenSijainti
                    .get(1).asDouble()});
        }

        return liikennepaikkaMap;
    }

    // Data format:
    // JRI -> {ArrayNode} "[{"tunniste":"1.2.245.578.9.01.23456","virallinenSijainti":[496612,6718700],"lyhenne":"Jri","nimiSe":null,"nimiEn":null}]"
    public InfraApiMapResult<JsonNode> getTrakediaLiikennepaikkaNodes() {
        final AtomicBoolean refreshed = new AtomicBoolean();
        final InfraApiMapResult<JsonNode> result = lpNodeCache.get(() -> {
            refreshed.set(true);
            return loadMap(nodeMapFailures, MapKind.NODE, () -> {
                final var liikennepaikkaMap = fetchNodeMap(liikennepaikatUrl);
                final var liikennepaikanOsaMap = fetchNodeMap(liikennepaikanosatUrl);

                // Counts are captured per source before merging, which destroys the split.
                final var sourceCounts = new EnumMap<InfraApiDataset, Integer>(InfraApiDataset.class);
                sourceCounts.put(InfraApiDataset.RAUTATIELIIKENNEPAIKAT, liikennepaikkaMap.size());
                sourceCounts.put(InfraApiDataset.LIIKENNEPAIKANOSAT, liikennepaikanOsaMap.size());

                final var mergedMap = new HashMap<>(liikennepaikkaMap);
                mergedMap.putAll(liikennepaikanOsaMap);
                return InfraApiMapResult.success(mergedMap, sourceCounts, Instant.now());
            });
        });
        return refreshed.get() ? result : result.asCacheHit();
    }

    /**
     * Runs a refresh unless a recent failure is still being suppressed, and emits the source-tier
     * event. This is the earliest point in the GTFS pipeline where degraded coverage is knowable.
     */
    private <V> ExpiringCache.CacheResult<InfraApiMapResult<V>> loadMap(final FailureWindow failures,
                                                                       final MapKind kind,
                                                                       final Supplier<InfraApiMapResult<V>> fetch) {
        final long startedAt = System.currentTimeMillis();

        if (failures.isSuppressed(Instant.now())) {
            final InfraApiMapResult<V> suppressed = InfraApiMapResult.failed(failures.lastFailure(), Instant.now(),
                    InfraApiMapResult.CacheState.REFRESH_SUPPRESSED);
            logSourceRefresh(kind, suppressed, startedAt);
            return new ExpiringCache.CacheResult<>(false, suppressed);
        }

        try {
            final InfraApiMapResult<V> result = fetch.get();
            failures.clear();
            logSourceRefresh(kind, result, startedAt);
            return new ExpiringCache.CacheResult<>(result.complete(), result);
        } catch (final RuntimeException e) {
            failures.record(e, Instant.now().plus(FAILURE_SUPPRESSION));
            final InfraApiMapResult<V> failed = InfraApiMapResult.failed(e, Instant.now(),
                    InfraApiMapResult.CacheState.REFRESH_FAILED);
            logSourceRefresh(kind, failed, startedAt);
            return new ExpiringCache.CacheResult<>(false, failed);
        }
    }

    /** The two maps have different consumers, so they must not share an entity type or namespace. */
    private record MapKind(String operation, String entityType, String metricPrefix) {
        private static final MapKind NODE =
                new MapKind("refreshInfraApiNodeMap", "infra_node_map", "rail.gtfs.nodes.");
        private static final MapKind COORDINATE =
                new MapKind("refreshInfraApiCoordinateMap", "infra_coordinate_map", "rail.infra.coordinates.");
    }

    private void logSourceRefresh(final MapKind kind, final InfraApiMapResult<?> result, final long startedAt) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("operation", kind.operation());
        event.put("outcome", result.complete() ? "success" : result.failure() == null ? "degraded" : "error");
        event.put("error.type", result.failure() == null ? "" : result.failure().getClass().getSimpleName());
        event.put("rail.source.system", "DIGITRAFFIC");
        event.put("rail.source.api", "infra-api");
        event.put("rail.source.owner", "TRAKEDIA");
        event.put("rail.entity.type", kind.entityType());
        event.put(kind.metricPrefix() + "cache.state", result.cacheState().name().toLowerCase(Locale.ROOT));
        for (final Map.Entry<InfraApiDataset, Integer> source : result.sourceCounts().entrySet()) {
            event.put(kind.metricPrefix() + source.getKey().metricKey() + ".count", source.getValue());
        }
        event.put("duration_ms", System.currentTimeMillis() - startedAt);

        if (result.complete()) {
            logger.info("{}", event);
        } else {
            logger.error("{}", event);
        }
    }

    /** Remembers the most recent refresh failure so it can be reported without re-fetching. */
    private static final class FailureWindow {
        private volatile Instant suppressedUntil = Instant.EPOCH;
        private volatile Throwable lastFailure;

        private boolean isSuppressed(final Instant now) {
            return now.isBefore(suppressedUntil);
        }

        private Throwable lastFailure() {
            return lastFailure;
        }

        private void record(final Throwable failure, final Instant until) {
            lastFailure = failure;
            suppressedUntil = until;
        }

        private void clear() {
            suppressedUntil = Instant.EPOCH;
            lastFailure = null;
        }
    }

    public Map<String, JsonNode> fetchNodeMap(final String url) {
        final Map<String, JsonNode> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return liikennepaikkaMap;
        }

        logger.info("method=fetchNodeMap Fetching Trakedia nodes from {}", url);

        final JsonNode jsonNode = retryTemplate.execute(context -> webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block());

        if (jsonNode == null) {
            throw new IllegalStateException("Infra-API returned null for " + url);
        }

        for (final JsonNode node : jsonNode) {
            final JsonNode lyhenne = node.get(0).get("lyhenne");
            liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), node);
        }

        return liikennepaikkaMap;
    }
}
