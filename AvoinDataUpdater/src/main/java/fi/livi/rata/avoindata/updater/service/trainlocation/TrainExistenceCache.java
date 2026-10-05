package fi.livi.rata.avoindata.updater.service.trainlocation;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * Caches train existence lookups by (departureDate, trainNumber) to reduce repeated DB queries
 * during train-location ingestion cycles.
 *
 * Positive results (train exists) are cached longer; negative results (train does not exist)
 * are cached briefly to avoid hiding trains inserted moments later.
 */
@Service
public class TrainExistenceCache {
    private final TrainRepository trainRepository;
    private final ConcurrentHashMap<TrainId, CachedValue> cache = new ConcurrentHashMap<>();

    private static final Duration POSITIVE_TTL = Duration.ofMinutes(10);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);

    @Autowired
    public TrainExistenceCache(final TrainRepository trainRepository) {
        this.trainRepository = trainRepository;
    }

    public void clear() {
        cache.clear();
    }

    /**
     * Returns whether a train with the given (departureDate, trainNumber) exists in the database.
     * Results are cached with TTL; negative results expire faster than positive ones.
     */
    public boolean exists(final LocalDate departureDate, final Long trainNumber) {
        final TrainId key = new TrainId(trainNumber, departureDate);
        final long nowMs = System.currentTimeMillis();

        final CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired(nowMs)) {
            return cached.exists;
        }

        final Long count = trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber);
        final boolean exists = count != null && count > 0;
        final long ttlMs = exists ? POSITIVE_TTL.toMillis() : NEGATIVE_TTL.toMillis();
        cache.put(key, new CachedValue(exists, nowMs + ttlMs));

        return exists;
    }

    /**
     * Internal cache entry with expiry tracking.
     */
        private record CachedValue(boolean exists, long expiresAtMs) {

        private boolean isExpired(final long nowMs) {
                return nowMs >= expiresAtMs;
            }
        }
}



