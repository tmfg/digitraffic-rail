package fi.livi.rata.avoindata.updater.service.netex;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Persists the NeTEx Nordic dataset: the ZIP package plus the resolved journey refs, stored together so a
 * partial publish can never happen. The whole generate-and-persist cycle is retried with exponential backoff;
 * if it still fails after all attempts the exception is rethrown and nothing is published.
 */
@Service
public class NeTExPackageService {

    private static final Logger log = LoggerFactory.getLogger(NeTExPackageService.class);

    private static final String PACKAGE_FILENAME = "FTR-netex.zip";

    // 1 initial attempt + 4 retries; backoff 2s, 4s, 8s, 16s (capped at 30s).
    private static final int MAX_ATTEMPTS = 5;
    private static final long BACKOFF_INITIAL_MS = 2000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long BACKOFF_MAX_MS = 30_000;

    private final NeTExService neTExService;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExPublishedJourneyWriter publishedJourneyWriter;
    private final TransactionTemplate transactionTemplate;
    private final RetryTemplate retryTemplate;

    public NeTExPackageService(final NeTExService neTExService,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExPublishedJourneyWriter publishedJourneyWriter,
            final PlatformTransactionManager transactionManager) {
        this.neTExService = neTExService;
        this.generatedExportRepository = generatedExportRepository;
        this.publishedJourneyWriter = publishedJourneyWriter;
        // A dedicated transaction for the atomic ZIP + journey-refs persist, independent of the (read-only)
        // compute step, so each retry attempt persists all-or-nothing.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .exponentialBackoff(BACKOFF_INITIAL_MS, BACKOFF_MULTIPLIER, BACKOFF_MAX_MS)
                .retryOn(Exception.class)
                .build();
    }

    /**
     * Generates and publishes the NeTEx package, retrying the whole cycle with exponential backoff. If every
     * attempt fails the final exception is rethrown (nothing is published).
     */
    public void generatePackage() {
        try {
            retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("method=generatePackage retrying NeTEx generation attempt={} of {}",
                            context.getRetryCount() + 1, MAX_ATTEMPTS);
                }
                generateAndPersist();
                return null;
            });
        } catch (final RuntimeException e) {
            // Every attempt failed and nothing was published. Emit one terminal ERROR so the give-up is a
            // first-class event (the per-attempt lines above are only WARN), then rethrow.
            log.error("event=rail.netex.package outcome=error error.type={} attempts={} message=\"NeTEx package "
                    + "not published after all retries\"", e.getClass().getSimpleName(), MAX_ATTEMPTS, e);
            throw e;
        }
    }

    /**
     * One attempt: compute + build the package, then persist the ZIP and the resolved journey refs in a single
     * transaction. If the persist fails the ZIP is not published (rolled back), so a failure here retries the
     * whole cycle rather than leaving a package without its journey refs.
     */
    private void generateAndPersist() {
        log.info("method=generatePackage starting NeTEx dataset generation");
        final long startTime = System.currentTimeMillis();

        final NeTExService.NeTExGenerationResult timetable = neTExService.generateNeTEx();
        if (timetable == null) {
            log.warn("method=generatePackage no timetable data, skipping package");
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            final GeneratedExport export = new GeneratedExport();
            export.data = timetable.zip();
            export.created = DateProvider.nowInHelsinki();
            export.fileName = PACKAGE_FILENAME;
            generatedExportRepository.persist(List.of(export));

            publishedJourneyWriter.persistWindow(timetable.dataset());
        });

        final long durationMs = System.currentTimeMillis() - startTime;
        log.info("method=generatePackage persisted {} size={} bytes files={} durationMs={}",
                PACKAGE_FILENAME, timetable.zip().length, timetable.files().size(), durationMs);
    }
}
