package fi.livi.rata.avoindata.server.controller.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "gtfs", description = "Returns trains as gtfs")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trains")
public class GtfsController {
    private final GeneratedExportRepository gtfsRepository;

    private static final int CACHE_SECONDS_FOR_RT_LOCATIONS = 10;
    private static final int CACHE_SECONDS_FOR_RT_UPDATES = 60;
    private static final int CACHE_SECONDS_FOR_STATIC = 60 * 15;
    /**
     * An older package never changes, so it can be cached far longer than the
     * current one.
     */
    private static final int CACHE_SECONDS_FOR_ARCHIVED = 60 * 60 * 24;

    private static final String DATE_PARAM_DESCRIPTION = "Return the package generated on this date instead of the current one. "
            + "Packages are kept for 14 days.";

    public GtfsController(final GeneratedExportRepository gtfsRepository) {
        this.gtfsRepository = gtfsRepository;
    }

    @Operation(summary = "Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-all.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForAllTrains(final HttpServletResponse response,
            @Parameter(description = DATE_PARAM_DESCRIPTION) @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            // Named on_date rather than date: a servlet filter copies any "date" parameter
            // into
            // "departure_date", which these endpoints do not declare, so the request would
            // be rejected.
            final LocalDate onDate) {
        return getData(response, "gtfs-all.zip", onDate, CACHE_SECONDS_FOR_STATIC);
    }

    @Operation(summary = "Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-passenger.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForPassengerTrains(final HttpServletResponse response,
            @Parameter(description = DATE_PARAM_DESCRIPTION) @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate onDate) {
        return getData(response, "gtfs-passenger.zip", onDate, CACHE_SECONDS_FOR_STATIC);
    }

    @Operation(summary = "Returns GTFS Realtime locations")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-rt-locations", produces = "application/protobuf")
    @Transactional(readOnly = true)
    public byte[] getGtfsRtLocations(final HttpServletResponse response,
            @Parameter(description = DATE_PARAM_DESCRIPTION) @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate onDate) {
        return getData(response, "gtfs-rt-locations", onDate, CACHE_SECONDS_FOR_RT_LOCATIONS);
    }

    @Operation(summary = "Returns GTFS Realtime updates")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-rt-updates", produces = "application/protobuf")
    @Transactional(readOnly = true)
    public byte[] getGtfsRtUpdates(final HttpServletResponse response,
            @Parameter(description = DATE_PARAM_DESCRIPTION) @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate onDate) {
        return getData(response, "gtfs-rt-updates", onDate, CACHE_SECONDS_FOR_RT_UPDATES);
    }

    @Hidden
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-vr-tre.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForVRTRETrains(final HttpServletResponse response,
            @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate onDate) {
        return getData(response, "gtfs-vr-tre.zip", onDate, CACHE_SECONDS_FOR_STATIC);
    }

    @Hidden
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-passenger-stops.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForPassengerNoNonstops(final HttpServletResponse response,
            @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate onDate) {
        return getData(response, "gtfs-passenger-stops.zip", onDate, CACHE_SECONDS_FOR_STATIC);
    }

    @Hidden
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-vr.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForVRTrains(final HttpServletResponse response,
            @RequestParam(name = "on_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate onDate) {
        return getData(response, "gtfs-vr.zip", onDate, CACHE_SECONDS_FOR_STATIC);
    }

    private byte[] getData(final HttpServletResponse response, final String fileName, final LocalDate onDate,
            final int cacheSeconds) {
        CacheControl.setCacheMaxAgeSeconds(response, onDate == null ? cacheSeconds : CACHE_SECONDS_FOR_ARCHIVED);

        final GeneratedExport gtfs = onDate == null
                ? gtfsRepository.findFirstByFileNameOrderByIdDesc(fileName)
                : gtfsRepository.findFirstByFileNameAndCreatedGreaterThanEqualAndCreatedLessThanOrderByIdDesc(
                        fileName,
                        onDate.atStartOfDay(DateProvider.ZONE_ID_HKI),
                        onDate.plusDays(1).atStartOfDay(DateProvider.ZONE_ID_HKI));
        if (gtfs == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return new byte[0];
        }

        response.addHeader("x-is-fresh",
                Boolean.toString(gtfs.created.isAfter(DateProvider.nowInHelsinki().minusHours(25))));
        response.addHeader("x-timestamp", gtfs.created.toString());
        response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(gtfs.data.length));

        return gtfs.data;
    }
}
