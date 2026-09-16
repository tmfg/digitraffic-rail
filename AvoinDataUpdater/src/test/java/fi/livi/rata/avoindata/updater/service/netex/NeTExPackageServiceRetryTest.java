package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;

/**
 * Retry behaviour of the package service: only a RIPA fetch failure is retried, and only after the
 * configured wait. Generation returning {@code null} (no data) is enough to exercise the loop, so these
 * tests never reach the persist step.
 */
class NeTExPackageServiceRetryTest {

    private NeTExService neTExService;
    private NeTExPublishedJourneyWriter publishedJourneyWriter;

    @BeforeEach
    void setUp() {
        neTExService = mock(NeTExService.class);
        publishedJourneyWriter = mock(NeTExPublishedJourneyWriter.class);
    }

    private NeTExPackageService serviceWith(final int attempts, final Duration delay) {
        return new NeTExPackageService(neTExService, mock(GeneratedExportRepository.class),
                publishedJourneyWriter, mock(PlatformTransactionManager.class), attempts, delay);
    }

    @Test
    void givenRipaFetchFails_whenGenerating_thenRetriesAfterTheConfiguredWait() {
        final NeTExPackageService service = serviceWith(2, Duration.ofMillis(200));
        when(neTExService.generateNeTEx())
                .thenThrow(new RipaFetchException("RIPA down", new RuntimeException("connect timeout")))
                .thenReturn(null);

        final long start = System.nanoTime();
        service.generatePackage();
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        verify(neTExService, times(2)).generateNeTEx();
        assertTrue(elapsedMs >= 200, "expected a wait before retrying, waited " + elapsedMs + " ms");
    }

    @Test
    void givenRipaFetchKeepsFailing_whenAttemptsExhausted_thenRethrows() {
        final NeTExPackageService service = serviceWith(2, Duration.ofMillis(1));
        final RipaFetchException failure = new RipaFetchException("RIPA down", new RuntimeException("boom"));
        when(neTExService.generateNeTEx()).thenThrow(failure);

        final RipaFetchException thrown = assertThrows(RipaFetchException.class, () -> service.generatePackage());

        assertSame(failure, thrown);
        verify(neTExService, times(2)).generateNeTEx();
        verify(publishedJourneyWriter, never()).persistWindow(any());
    }

    @Test
    void givenAttemptsIsOne_whenRipaFetchFails_thenNoRetry() {
        // a 30 minute delay would hang the test if a retry were attempted
        final NeTExPackageService service = serviceWith(1, Duration.ofMinutes(30));
        when(neTExService.generateNeTEx())
                .thenThrow(new RipaFetchException("RIPA down", new RuntimeException("boom")));

        assertThrows(RipaFetchException.class, () -> service.generatePackage());

        verify(neTExService, times(1)).generateNeTEx();
    }

    @Test
    void givenNonRipaFailure_whenGenerating_thenNotRetried() {
        final NeTExPackageService service = serviceWith(3, Duration.ofMinutes(30));
        when(neTExService.generateNeTEx()).thenThrow(new IllegalStateException("PETI match rate too low"));

        assertThrows(IllegalStateException.class, () -> service.generatePackage());

        verify(neTExService, times(1)).generateNeTEx();
    }

    @Test
    void givenFirstAttemptSucceeds_whenGenerating_thenCalledOnce() {
        final NeTExPackageService service = serviceWith(2, Duration.ofMinutes(30));
        when(neTExService.generateNeTEx()).thenReturn(null);

        service.generatePackage();

        verify(neTExService, times(1)).generateNeTEx();
    }
}
