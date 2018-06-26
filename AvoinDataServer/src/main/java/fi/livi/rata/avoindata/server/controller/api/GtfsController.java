package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSRepository;
import fi.livi.rata.avoindata.server.config.WebConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "trains", description = "Returns trains", position = Integer.MIN_VALUE)
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trains")
public class GtfsController {

    @Autowired
    private GTFSRepository gtfsRepository;

    @ApiOperation("Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-all.zip")
    public byte[] getGtfsForAllTrains() {
        return gtfsRepository.findFirstByFileNameOrderByIdDesc("gtfs-all.zip").data;
    }

    @ApiOperation("Returns GTFS zip file")
    @RequestMapping(method = RequestMethod.GET, path = "gtfs-passenger.zip")
    public byte[] getGtfsForPassengerTrains() {
        return gtfsRepository.findFirstByFileNameOrderByIdDesc("gtfs-passenger.zip").data;
    }
}
