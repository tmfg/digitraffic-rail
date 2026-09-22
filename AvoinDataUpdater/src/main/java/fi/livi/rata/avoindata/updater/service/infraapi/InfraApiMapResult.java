package fi.livi.rata.avoindata.updater.service.infraapi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * A merged Infra API map together with the provenance needed to judge it. Merging destroys the
 * per-source split, so the counts must travel with the value rather than being read back from a
 * side channel that is stale whenever the cache is warm.
 */
public record InfraApiMapResult<V>(Map<String, V> values,
                                   Map<InfraApiDataset, Integer> sourceCounts,
                                   CacheState cacheState,
                                   Instant fetchedAt,
                                   Throwable failure) {

    public enum CacheState {
        HIT,
        MISS,
        REFRESH_FAILED
    }

    public static <V> InfraApiMapResult<V> success(final Map<String, V> values,
                                                   final Map<InfraApiDataset, Integer> sourceCounts,
                                                   final Instant fetchedAt) {
        return new InfraApiMapResult<>(values, sourceCounts, CacheState.MISS, fetchedAt, null);
    }

    public static <V> InfraApiMapResult<V> failed(final Throwable failure, final Instant at,
                                                  final CacheState cacheState) {
        return new InfraApiMapResult<>(Map.of(), Map.of(), cacheState, at, failure);
    }

    public InfraApiMapResult<V> asCacheHit() {
        return new InfraApiMapResult<>(values, sourceCounts, CacheState.HIT, fetchedAt, failure);
    }

    /** True when every required source returned data. */
    public boolean complete() {
        return failure == null && !sourceCounts.isEmpty()
                && sourceCounts.values().stream().allMatch(count -> count > 0);
    }

    public int countOf(final InfraApiDataset dataset) {
        return sourceCounts.getOrDefault(dataset, 0);
    }

    public Duration age(final Instant now) {
        return Duration.between(fetchedAt, now);
    }

    /**
     * Best-effort view for callers that have a legitimate fallback and must not fail the whole
     * ingestion because an optional index is unavailable.
     */
    public Map<String, V> valuesOrEmpty() {
        return values == null ? Map.of() : values;
    }

    /**
     * Strict view for callers that would otherwise publish a structurally valid but wrong artefact
     * from partial coverage.
     */
    public Map<String, V> requireComplete() {
        if (!complete()) {
            throw new IncompleteInfraApiMapException(this);
        }
        return values;
    }

    public static class IncompleteInfraApiMapException extends IllegalStateException {
        public IncompleteInfraApiMapException(final InfraApiMapResult<?> result) {
            super("Infra-API map is incomplete: sourceCounts=" + result.sourceCounts()
                    + " cacheState=" + result.cacheState(), result.failure());
        }
    }
}
