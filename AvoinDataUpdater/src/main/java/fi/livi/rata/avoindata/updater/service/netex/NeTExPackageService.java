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
    private final GeneratedExportRepository generatedExportRepository;

    public NeTExPackageService(final NeTExService neTExService,
            final GeneratedExportRepository generatedExportRepository) {
        this.neTExService = neTExService;
        this.generatedExportRepository = generatedExportRepository;
    }

    @Transactional
    public void generatePackage() {
        log.info("method=generatePackage starting NeTEx dataset generation");
        final long startTime = System.currentTimeMillis();

        try {
            final NeTExService.NeTExGenerationResult timetable = neTExService.generateNeTEx();
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
            log.info("method=generatePackage persisted {} size={} bytes files={} durationMs={}",
                    PACKAGE_FILENAME, zip.length, timetable.files().size(), durationMs);
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            log.error("method=generatePackage failed durationMs={}", durationMs, e);
            throw new RuntimeException("NeTEx package generation failed", e);
        }
    }
}
