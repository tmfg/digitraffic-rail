package fi.livi.rata.avoindata.server.controller.api.metadata;


import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import fi.livi.rata.avoindata.server.services.GeoJsonFormatter;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.function.Function;

@RestController
public class StationController extends AMetadataController {
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private GeoJsonFormatter geoJsonFormatter;

    private Function<Station, Double[]> converter = s -> new Double[]{s.longitude.doubleValue(), s.latitude.doubleValue()};

    @ApiOperation("Returns list of stations")
    @RequestMapping(value = "stations", method = RequestMethod.GET)
    public List<Station> getStations(HttpServletResponse response) {
        final List<Station> list = stationRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        setCache(response, list);
        return list;
    }

    @ApiOperation("Returns list of stations in geojson format")
    @RequestMapping(value = "stations.geojson", method = RequestMethod.GET, produces = "application/vnd.geo+json")
    public FeatureCollection getStationsAsGeoJson(HttpServletResponse response) {
        return geoJsonFormatter.wrapAsGeoJson(this.getStations(response), converter);
    }
}
