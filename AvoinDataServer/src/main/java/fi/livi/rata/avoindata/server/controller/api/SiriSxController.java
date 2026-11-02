package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the SIRI Situation Exchange (SX) feed: passenger information messages. A separate controller and flag, so
 * each SIRI feed can be switched on and off on its own.
 */
@Tag(name = "siri", description = "Returns real-time data in SIRI Nordic format")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "siri")
// No matchIfMissing: the endpoint is disabled unless explicitly enabled.
@ConditionalOnProperty(name = "avoindataserver.siri.sx.enabled", havingValue = "true")
public class SiriSxController {

    private static final String SX_FILENAME = "siri-sx.xml";
    private static final int CACHE_SECONDS = 30;
    // The updater checks the messages ~every minute and refreshes the file time even when nothing has changed.
    private static final int FRESH_WITHIN_MINUTES = 5;

    private final GeneratedExportRepository generatedExportRepository;

    public SiriSxController(final GeneratedExportRepository generatedExportRepository) {
        this.generatedExportRepository = generatedExportRepository;
    }

    @Operation(
            summary = "Returns SIRI Nordic Situation Exchange (SX) XML",
            description = """
                    Passenger information messages shown on train and station displays and spoken in announcements.

                    Station messages point to stops with `StopPlaceRef`, and train messages to a train with
                    `FramedVehicleJourneyRef`. Stop place identifiers are from Fintraffic's national stop registry (PETI).

                    [Browse rail stops in PETI](https://peti.fintraffic.fi/api/fintraffic/v1/stops?transportModes=rail).
                    """)
    @RequestMapping(method = RequestMethod.GET, path = "sx", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getSiriSx() {
        final GeneratedExport export = generatedExportRepository.findFirstByFileNameOrderByIdDesc(SX_FILENAME);
        if (export == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .headers(CacheControl.exportHeaders(export, CACHE_SECONDS, FRESH_WITHIN_MINUTES))
                .body(export.data);
    }
}
