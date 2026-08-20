package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;

/**
 * Assembles the single NeTEx Nordic dataset ZIP from the timetable and
 * composition deliveries and persists it. All member files ({@code _FTR_shared_data.xml},
 * {@code FTR_timetables.xml}, {@code FTR_compositions.xml}) share one OperatingDay
 * calendar so cross-file references resolve within the one archive.
 */
@Service
public class NeTExPackageService {

    private static final Logger log = LoggerFactory.getLogger(NeTExPackageService.class);

    private static final String PACKAGE_FILENAME = "FTR-netex.zip";
    private static final String SHARED_DATA_XML = "_FTR_shared_data.xml";
    private static final String TIMETABLES_XML = "FTR_timetables.xml";
    private static final String COMPOSITIONS_XML = "FTR_compositions.xml";

    private final NeTExService neTExService;
    private final NeTExCompositionService compositionService;
    private final NeTExWritingService writingService;
    private final GeneratedExportRepository generatedExportRepository;

    public NeTExPackageService(final NeTExService neTExService,
            final NeTExCompositionService compositionService,
            final NeTExWritingService writingService,
            final GeneratedExportRepository generatedExportRepository) {
        this.neTExService = neTExService;
        this.compositionService = compositionService;
        this.writingService = writingService;
        this.generatedExportRepository = generatedExportRepository;
    }

    @Transactional
    public void generatePackage() {
        log.info("method=generatePackage starting combined NeTEx dataset generation");
        final long startTime = System.currentTimeMillis();

        try {
            final NeTExService.NeTExGenerationResult timetable = neTExService.generateNeTEx();
            if (timetable == null) {
                log.warn("method=generatePackage no timetable data, skipping package");
                return;
            }

            final NeTExCompositionService.CompositionDelivery compositions = compositionService
                    .buildCompositionDelivery();

            final Set<LocalDate> operatingDays = new TreeSet<>(timetable.operatingDays());
            if (compositions != null) {
                operatingDays.addAll(compositions.operatingDays());
            }

            final ZonedDateTime timestamp = ZonedDateTime.now();
            final Map<String, PublicationDeliveryStructure> files = new LinkedHashMap<>();
            files.put(SHARED_DATA_XML, writingService.buildOperatingDaySharedData(operatingDays, timestamp));
            files.put(TIMETABLES_XML, timetable.timetableDelivery());
            if (compositions != null) {
                files.put(COMPOSITIONS_XML, compositions.delivery());
            }
            final byte[] zip = writingService.marshalAndZip(files);

            final GeneratedExport export = new GeneratedExport();
            export.data = zip;
            export.created = ZonedDateTime.now();
            export.fileName = PACKAGE_FILENAME;
            generatedExportRepository.persist(List.of(export));

            final long durationMs = System.currentTimeMillis() - startTime;
            log.info("method=generatePackage persisted {} size={} bytes compositions_included={} durationMs={}",
                    PACKAGE_FILENAME, zip.length, compositions != null, durationMs);
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            log.error("method=generatePackage failed durationMs={}", durationMs, e);
            throw new RuntimeException("NeTEx package generation failed", e);
        }
    }
}
