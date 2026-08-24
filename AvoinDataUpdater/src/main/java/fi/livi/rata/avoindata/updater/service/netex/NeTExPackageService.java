package fi.livi.rata.avoindata.updater.service.netex;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;

/**
 * Persists the NeTEx Nordic dataset: one common file plus a file per Line, all
 * sharing one OperatingDay calendar so cross-file references resolve within the
 * single archive.
 */
@Service
public class NeTExPackageService {

    private static final Logger log = LoggerFactory.getLogger(NeTExPackageService.class);

    private static final String PACKAGE_FILENAME = "FTR-netex.zip";

    private final NeTExService neTExService;
    private final NeTExCompositionService compositionService;
    private final GeneratedExportRepository generatedExportRepository;

    public NeTExPackageService(final NeTExService neTExService,
            final NeTExCompositionService compositionService,
            final GeneratedExportRepository generatedExportRepository) {
        this.neTExService = neTExService;
        this.compositionService = compositionService;
        this.generatedExportRepository = generatedExportRepository;
    }

    @Transactional
    public void generatePackage() {
        log.info("method=generatePackage starting combined NeTEx dataset generation");
        final long startTime = System.currentTimeMillis();

        try {
            // Compositions are resolved first so the line files can carry the train
            // formation of the journeys they describe.
            final NeTExCompositionService.CompositionData compositions = compositionService.buildCompositionData();
            final NeTExService.NeTExGenerationResult timetable = neTExService.generateNeTEx(compositions);
            if (timetable == null) {
                log.warn("method=generatePackage no timetable data, skipping package");
                return;
            }

            final byte[] zip = timetable.zip();

            final GeneratedExport export = new GeneratedExport();
            export.data = zip;
            export.created = ZonedDateTime.now();
            export.fileName = PACKAGE_FILENAME;
            generatedExportRepository.persist(List.of(export));

            final long durationMs = System.currentTimeMillis() - startTime;
            log.info("method=generatePackage persisted {} size={} bytes files={} compositions={} durationMs={}",
                    PACKAGE_FILENAME, zip.length, timetable.files().size(),
                    compositions.datedJourneys().size(), durationMs);
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            log.error("method=generatePackage failed durationMs={}", durationMs, e);
            throw new RuntimeException("NeTEx package generation failed", e);
        }
    }
}
