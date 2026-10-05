package fi.livi.rata.avoindata.updater.service.trainlocation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;

@ExtendWith(MockitoExtension.class)
public class TrainExistenceCacheTest {
    @Mock
    private TrainRepository trainRepository;
    private TrainExistenceCache cache;

    @BeforeEach
    public void setup() {
        cache = new TrainExistenceCache(trainRepository);
    }

    @Test
    public void firstLookupQueriesRepository() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        final Long trainNumber = 1L;
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber))
                .thenReturn(1L);
        final boolean result = cache.exists(departureDate, trainNumber);
        assertTrue(result);
        verify(trainRepository, times(1)).existsByDepartureDateAndTrainNumber(departureDate, trainNumber);
    }

    @Test
    public void secondLookupUsesCache() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        final Long trainNumber = 1L;
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber))
                .thenReturn(1L);
        cache.exists(departureDate, trainNumber);
        cache.exists(departureDate, trainNumber);
        verify(trainRepository, times(1)).existsByDepartureDateAndTrainNumber(departureDate, trainNumber);
    }

    @Test
    public void positiveCachesTrainExists() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        final Long trainNumber = 2L;
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber))
                .thenReturn(1L);
        final boolean result = cache.exists(departureDate, trainNumber);
        assertTrue(result);
    }

    @Test
    public void negativeCachesTrainDoesNotExist() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        final Long trainNumber = 9999L;
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber))
                .thenReturn(0L);
        final boolean result = cache.exists(departureDate, trainNumber);
        assertFalse(result);
    }

    @Test
    public void differentTrainNumbersAreNotCrossContaminated() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, 1L))
                .thenReturn(1L);
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, 2L))
                .thenReturn(0L);
        assertTrue(cache.exists(departureDate, 1L));
        assertFalse(cache.exists(departureDate, 2L));
        verify(trainRepository, times(1)).existsByDepartureDateAndTrainNumber(departureDate, 1L);
        verify(trainRepository, times(1)).existsByDepartureDateAndTrainNumber(departureDate, 2L);
    }

    @Test
    public void expiredPositiveEntryIsRecomputed() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        final Long trainNumber = 3L;
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber))
                .thenReturn(1L, 0L);

        assertTrue(cache.exists(departureDate, trainNumber));
        expireCacheEntry(departureDate, trainNumber, true);

        assertFalse(cache.exists(departureDate, trainNumber));
        verify(trainRepository, times(2)).existsByDepartureDateAndTrainNumber(departureDate, trainNumber);
    }

    @Test
    public void expiredNegativeEntryIsRecomputed() {
        final LocalDate departureDate = LocalDate.of(2026, 10, 2);
        final Long trainNumber = 9999L;
        when(trainRepository.existsByDepartureDateAndTrainNumber(departureDate, trainNumber))
                .thenReturn(0L, 1L);

        assertFalse(cache.exists(departureDate, trainNumber));
        expireCacheEntry(departureDate, trainNumber, false);

        assertTrue(cache.exists(departureDate, trainNumber));
        verify(trainRepository, times(2)).existsByDepartureDateAndTrainNumber(departureDate, trainNumber);
    }

    @SuppressWarnings("unchecked")
    private void expireCacheEntry(final LocalDate departureDate, final Long trainNumber, final boolean exists) {
        try {
            final TrainId key = new TrainId(trainNumber, departureDate);
            final Field cacheField = TrainExistenceCache.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            final ConcurrentHashMap<TrainId, Object> cacheMap = (ConcurrentHashMap<TrainId, Object>) cacheField.get(cache);

            final Class<?> cachedValueClass = Class.forName(TrainExistenceCache.class.getName() + "$CachedValue");
            final Constructor<?> constructor = cachedValueClass.getDeclaredConstructor(boolean.class, long.class);
            constructor.setAccessible(true);
            cacheMap.put(key, constructor.newInstance(exists, System.currentTimeMillis() - 1));
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Failed to force cache expiry for test", e);
        }
    }
}
