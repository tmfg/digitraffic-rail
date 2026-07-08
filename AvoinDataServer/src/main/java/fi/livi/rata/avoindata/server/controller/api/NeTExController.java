package fi.livi.rata.avoindata.server.controller.api;

import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "netex", description = "Returns trains in NeTEx Nordic format)")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trains")
public class NeTExController {

    private static final String NETEX_FILENAME = "netex-nordic-timetables.zip";
    private static final String COMPOSITIONS_FILENAME = "netex-nordic-compositions.zip";
    private static final int CACHE_SECONDS = 60 * 15;

    private final GeneratedExportRepository generatedExportRepository;

    public NeTExController(final GeneratedExportRepository generatedExportRepository) {
        this.generatedExportRepository = generatedExportRepository;
    }

    @Operation(summary = "Returns NeTEx Nordic static timetable ZIP")
    @RequestMapping(method = RequestMethod.GET, path = "netex-nordic-timetables.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getNeTExNordic(final HttpServletResponse response) {
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_SECONDS);

        final GeneratedExport export = generatedExportRepository.findFirstByFileNameOrderByIdDesc(NETEX_FILENAME);

        response.addHeader("x-is-fresh",
                Boolean.toString(export.created.isAfter(DateProvider.nowInHelsinki().minusHours(25))));
        response.addHeader("x-timestamp", export.created.toString());
        response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(export.data.length));

        return export.data;
    }

    @Operation(summary = "Returns NeTEx Nordic compositions and accessibility ZIP")
    @RequestMapping(method = RequestMethod.GET, path = "netex-nordic-compositions.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getNeTExCompositions(final HttpServletResponse response) {
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_SECONDS);

        final GeneratedExport export = generatedExportRepository
                .findFirstByFileNameOrderByIdDesc(COMPOSITIONS_FILENAME);
        if (export == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return new byte[0];
        }

        response.addHeader("x-is-fresh",
                Boolean.toString(export.created.isAfter(DateProvider.nowInHelsinki().minusHours(25))));
        response.addHeader("x-timestamp", export.created.toString());
        response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(export.data.length));

        return export.data;
    }
}
