package fi.livi.rata.avoindata.server.localization;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public abstract class LocalizationCache<K, V> {
    private static Logger log = LoggerFactory.getLogger(LocalizationCache.class);
    public static final String UNKNOWN_NAME = "unknown";
    private final V unknown;
    private final Function<K, V> dataSupplier;

    private final CacheLoader<K, V> loader = new CacheLoader<K, V>() {
        @Override
        public V load(final K id) throws Exception {
            return dataSupplier.apply(id);
        }
    };

    protected LocalizationCache(final V unknown, final Function<K, V> dataSupplier) {
        this.unknown = unknown;
        this.dataSupplier = dataSupplier;
    }

    private final LoadingCache<K, V> cache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(loader);

    public final V get(final K key) {
        try {
            return cache.get(key);
        } catch (final ExecutionException ignored) {
            return returnUnknown(key);
        } catch (final CacheLoader.InvalidCacheLoadException ignored) {
            return returnUnknown(key);
        }
    }

    private V returnUnknown(final K key) {
        log.error("Cache key {} not found. Returning {}", key, unknown);
        return unknown;
    }
}
