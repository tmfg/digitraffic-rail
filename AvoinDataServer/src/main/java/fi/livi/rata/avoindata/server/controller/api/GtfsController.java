package fi.livi.rata.avoindata.server.controller.api;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFS;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(tags = "trains", description = "Returns trains as gtfs", position = Integer.MIN_VALUE)
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trains")
public class GtfsController {
    @Autowired
    private DateProvider dp;

    @Autowired
    private GTFSRepository gtfsRepository;

    @ApiOperation("Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-all.zip")
    public byte[] getGtfsForAllTrains(HttpServletResponse response) {
        GTFS gtfs = gtfsRepository.findFirstByFileNameOrderByIdDesc("gtfs-all.zip");
        response.addHeader("is-fresh", Boolean.toString(gtfs.created.isAfter(dp.nowInHelsinki().minusHours(25))));
        return gtfs.data;
    }

    @ApiOperation("Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-passenger.zip")
    public byte[] getGtfsForPassengerTrains(HttpServletResponse response) {
        GTFS gtfs = gtfsRepository.findFirstByFileNameOrderByIdDesc("gtfs-passenger.zip");
        response.addHeader("is-fresh", Boolean.toString(gtfs.created.isAfter(dp.nowInHelsinki().minusHours(25))));
        return gtfs.data;
    }
}
