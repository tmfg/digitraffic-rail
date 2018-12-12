package fi.livi.rata.avoindata.server.controller.api.metadata;


import com.amazonaws.xray.spring.aop.XRayEnabled;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
public class StationController extends AMetadataController {
    @Autowired
    private StationRepository stationRepository;

    @ApiOperation("Returns list of stations")
    @RequestMapping(value = "stations",method = RequestMethod.GET)
    public List<Station> getStations(HttpServletResponse response) {
        final List<Station> list = stationRepository.findAll(new Sort(Sort.Direction.ASC, "name"));
        setCache(response, list);
        return list;
    }
}
