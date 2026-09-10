package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the SIRI Nordic Vehicle Monitoring (VM) real-time feed. A separate controller (and flag) from
 * {@link SiriController}'s SIRI-ET endpoint, since the two feeds must be independently toggleable —
 * {@code @ConditionalOnProperty} only gates whole bean registration, not individual methods.
 */
@Tag(name = "siri", description = "Returns real-time data in SIRI Nordic format")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "siri")
// No matchIfMissing: the endpoint is disabled unless explicitly enabled.
@ConditionalOnProperty(name = "avoindataserver.siri.vm.enabled", havingValue = "true")
public class SiriVmController {

    private static final String VM_FILENAME = "siri-vm.xml";
    private static final int CACHE_SECONDS = 30;
    // Live positions are regenerated ~every minute; older than this is stale.
    private static final int FRESH_WITHIN_MINUTES = 5;

    private final GeneratedExportRepository generatedExportRepository;

    public SiriVmController(final GeneratedExportRepository generatedExportRepository) {
        this.generatedExportRepository = generatedExportRepository;
    }

    @Operation(summary = "Returns SIRI Nordic Vehicle Monitoring (VM) real-time XML")
    @RequestMapping(method = RequestMethod.GET, path = "vm", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getSiriVm() {
        final GeneratedExport export = generatedExportRepository.findFirstByFileNameOrderByIdDesc(VM_FILENAME);
        if (export == null) {
            return ResponseEntity.notFound().build();
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(String.format("max-age=%d, public", CACHE_SECONDS));
        headers.add("x-is-fresh",
                Boolean.toString(export.created.isAfter(DateProvider.nowInHelsinki().minusMinutes(FRESH_WITHIN_MINUTES))));
        headers.add("x-timestamp", export.created.toString());
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(export.data.length));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .headers(headers)
                .body(export.data);
    }
}
