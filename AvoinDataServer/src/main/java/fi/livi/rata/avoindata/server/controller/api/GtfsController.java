package fi.livi.rata.avoindata.server.controller.api;

import jakarta.servlet.http.HttpServletResponse;

import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFS;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "trains", description = "Returns trains as gtfs")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trains")
public class GtfsController {
    @Autowired
    private DateProvider dp;

    @Autowired
    private GTFSRepository gtfsRepository;

    @Operation(summary = "Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-all.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForAllTrains(final HttpServletResponse response) {
        return getData(response, "gtfs-all.zip");
    }

    @Operation(summary = "Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-passenger.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForPassengerTrains(final HttpServletResponse response) {
        return getData(response, "gtfs-passenger.zip");
    }

    @Operation(summary = "Returns GTFS Realtime locations")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-rt-locations", produces = "application/protobuf")
    @Transactional(readOnly = true)
    public byte[] getGtfsRtLocations(final HttpServletResponse response) {
        CacheControl.setCacheMaxAgeSeconds(response, 10);
        return getData(response, "gtfs-rt-locations");
    }

    @Operation(summary = "Returns GTFS Realtime updates")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-rt-updates", produces = "application/protobuf")
    @Transactional(readOnly = true)
    public byte[] getGtfsRtUpdates(final HttpServletResponse response) {
        CacheControl.setCacheMaxAgeSeconds(response, 60);
        return getData(response, "gtfs-rt-updates");
    }

    @Hidden
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-vr-tre.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForVRTRETrains(final HttpServletResponse response) {
        return getData(response, "gtfs-vr-tre.zip");
    }

    @Hidden
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-passenger-stops.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForPassengerNoNonstops(final HttpServletResponse response) {
        return getData(response, "gtfs-passenger-stops.zip");
    }

    @Hidden
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-vr.zip", produces = "application/zip")
    @Transactional(readOnly = true)
    public byte[] getGtfsForVRTrains(final HttpServletResponse response) {
        return getData(response, "gtfs-vr.zip");
    }

    private byte[] getData(final HttpServletResponse response, final String fileName) {
        final GTFS gtfs = gtfsRepository.findFirstByFileNameOrderByIdDesc(fileName);

        response.addHeader("x-is-fresh", Boolean.toString(gtfs.created.isAfter(dp.nowInHelsinki().minusHours(25))));
        response.addHeader("x-timestamp", gtfs.created.toString());

        return gtfs.data;
    }
}
