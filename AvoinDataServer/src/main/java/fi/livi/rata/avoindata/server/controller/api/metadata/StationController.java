package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.List;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import fi.livi.rata.avoindata.server.services.GeoJsonFormatter;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class StationController extends AMetadataController {
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private GeoJsonFormatter geoJsonFormatter;

    private static final Function<Station, Double[]> converter = s -> new Double[]{s.longitude.doubleValue(), s.latitude.doubleValue()};

    @Operation(summary = "Returns list of stations")
    @RequestMapping(value = "stations", method = RequestMethod.GET)
    public List<Station> getStations(final HttpServletResponse response) {
        final List<Station> list = stationRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        setCache(response, list);
        return list;
    }

    @Operation(summary = "Returns list of stations in geojson format")
    @RequestMapping(value = "stations.geojson", method = RequestMethod.GET, produces = "application/vnd.geo+json")
    public FeatureCollection getStationsAsGeoJson(final HttpServletResponse response) {
        return geoJsonFormatter.wrapAsGeoJson(this.getStations(response), converter);
    }
}
