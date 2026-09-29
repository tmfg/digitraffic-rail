package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Persists the NeTEx Nordic dataset: the ZIP package plus the resolved journey
 * refs, stored together so a
 * partial publish can never happen. Only a {@link RipaFetchException} is
 * retried, after a wait — RIPA
 * being briefly unavailable is the one failure a later attempt can fix, while
 * the build that follows is
 * deterministic and would fail the same way. Anything else is logged and
 * rethrown, and the next scheduled
 * run is the retry.
 */
@Service
public class NeTExPackageService {

    private static final Logger log = LoggerFactory.getLogger(NeTExPackageService.class);

    private static final String PACKAGE_FILENAME = "FTR-netex.zip";

    private final NeTExService neTExService;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExPublishedJourneyWriter publishedJourneyWriter;
    private final TransactionTemplate transactionTemplate;
    private final RetryTemplate retryTemplate;
    /** Total attempts, so 1 disables retrying. */
    private final int retryAttempts;
    private final Duration retryDelay;

    public NeTExPackageService(final NeTExService neTExService,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExPublishedJourneyWriter publishedJourneyWriter,
            final PlatformTransactionManager transactionManager,
            @Value("${updater.netex.ripa-retry.attempts:2}") final int retryAttempts,
            @Value("${updater.netex.ripa-retry.delay:PT5M}") final Duration retryDelay) {
        this.neTExService = neTExService;
        this.generatedExportRepository = generatedExportRepository;
        this.publishedJourneyWriter = publishedJourneyWriter;
        this.retryAttempts = retryAttempts;
        this.retryDelay = retryDelay;
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(retryAttempts)
                .fixedBackoff(retryDelay)
                .retryOn(RipaFetchException.class)
                .withListener(new RetryScheduledLogger())
                .build();
        // A dedicated transaction for the atomic ZIP + journey-refs persist,
        // independent of the (read-only)
        // compute step, so the publish is all-or-nothing.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Generates the package, retrying the whole run when RIPA could not be reached.
     * The wait between
     * attempts keeps the retry from landing in the middle of the same outage.
     */
    public void generatePackage() {
        try {
            retryTemplate.execute(context -> {
                generateOnce();
                return null;
            });
        } catch (final BackOffInterruptedException interrupted) {
            // the policy has already restored the interrupt flag
            log.warn("event=generateNeTEx method=generatePackage outcome=retry_abandoned "
                    + "message=\"interrupted while waiting to retry\"");
            throw interrupted;
        } catch (final RipaFetchException e) {
            final Throwable root = rootCause(e);
            log.error("event=generateNeTEx method=generatePackage "
                    + "outcome=error error.type={} error.root.type={} error.root.message=\"{}\" "
                    + "attempts={} message=\"NeTEx package not published, RIPA fetch failed on every "
                    + "attempt\"",
                    e.getClass().getName(), root.getClass().getName(), root.getMessage(), retryAttempts, e);
            throw e;
        }
    }

    /**
     * Logs an attempt that will be followed by another; the final failure is logged
     * by the caller.
     */
    private final class RetryScheduledLogger implements RetryListener {
        @Override
        public <T, E extends Throwable> void onError(final RetryContext context,
                final RetryCallback<T, E> callback, final Throwable throwable) {
            if (throwable instanceof RipaFetchException && context.getRetryCount() < retryAttempts) {
                log.warn("event=generateNeTEx method=generatePackage outcome=retry_scheduled "
                        + "error.type={} attempt={} attempts={} retry_delay={} "
                        + "message=\"RIPA fetch failed, retrying\"",
                        throwable.getClass().getName(), context.getRetryCount(), retryAttempts, retryDelay);
            }
        }
    }

    /**
     * Computes and builds the package, then persists the ZIP and the resolved
     * journey refs in a single
     * transaction, so a failed persist leaves no package without its journey refs.
     * The exception is rethrown
     * after logging so the caller still sees the run as failed.
     */
    private void generateOnce() {
        final long startTime = System.currentTimeMillis();

        try {
            final NeTExService.NeTExGenerationResult timetable = neTExService.generateNeTEx();
            if (timetable == null) {
                log.warn("event=generateNeTEx method=generatePackage no timetable data, skipping package");
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
            log.info("event=generateNeTEx method=generatePackage persisted {} size={} bytes files={} "
                    + "durationMs={}",
                    PACKAGE_FILENAME, timetable.zip().length, timetable.files().size(), durationMs);
        } catch (final RuntimeException e) {
            // A RIPA outage may still be retried, so the terminal error event is left to
            // the retry loop.
            if (e instanceof RipaFetchException) {
                throw e;
            }
            final Throwable root = rootCause(e);
            log.error("event=generateNeTEx method=generatePackage outcome=error "
                    + "error.type={} "
                    + "error.message=\"{}\" error.root.type={} error.root.message=\"{}\" durationMs={} "
                    + "message=\"NeTEx package not published\"",
                    e.getClass().getName(), e.getMessage(), root.getClass().getName(), root.getMessage(),
                    System.currentTimeMillis() - startTime, e);
            throw e;
        }
    }

    /**
     * Spring and Hibernate wrap the message that names the offending column several
     * layers deep.
     */
    private static Throwable rootCause(final Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
