package fi.livi.rata.avoindata.updater.service.netex;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Persists the NeTEx Nordic dataset: the ZIP package plus the resolved journey refs, stored together so a
 * partial publish can never happen. A failed run is not retried — generation is expensive enough that an
 * immediate second attempt mostly repeats the conditions that broke the first — so the failure is logged
 * and rethrown, and the next scheduled run is the retry.
 */
@Service
public class NeTExPackageService {

    private static final Logger log = LoggerFactory.getLogger(NeTExPackageService.class);

    private static final String PACKAGE_FILENAME = "FTR-netex.zip";

    private final NeTExService neTExService;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExPublishedJourneyWriter publishedJourneyWriter;
    private final TransactionTemplate transactionTemplate;

    public NeTExPackageService(final NeTExService neTExService,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExPublishedJourneyWriter publishedJourneyWriter,
            final PlatformTransactionManager transactionManager) {
        this.neTExService = neTExService;
        this.generatedExportRepository = generatedExportRepository;
        this.publishedJourneyWriter = publishedJourneyWriter;
        // A dedicated transaction for the atomic ZIP + journey-refs persist, independent of the (read-only)
        // compute step, so the publish is all-or-nothing.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Computes and builds the package, then persists the ZIP and the resolved journey refs in a single
     * transaction, so a failed persist leaves no package without its journey refs. The exception is rethrown
     * after logging so the caller still sees the run as failed.
     */
    public void generatePackage() {
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
            final Throwable root = rootCause(e);
            log.error("event=generateNeTEx method=generatePackage wide_event=rail.netex.package outcome=error "
                    + "error.type={} "
                    + "error.message=\"{}\" error.root.type={} error.root.message=\"{}\" durationMs={} "
                    + "message=\"NeTEx package not published\"",
                    e.getClass().getName(), e.getMessage(), root.getClass().getName(), root.getMessage(),
                    System.currentTimeMillis() - startTime, e);
            throw e;
        }
    }

    /** Spring and Hibernate wrap the message that names the offending column several layers deep. */
    private static Throwable rootCause(final Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
