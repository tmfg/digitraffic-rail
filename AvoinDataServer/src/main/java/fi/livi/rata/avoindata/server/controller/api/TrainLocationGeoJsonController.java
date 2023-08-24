package fi.livi.rata.avoindata.server.controller.api;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import jakarta.servlet.http.HttpServletResponse;

import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.services.GeoJsonFormatter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "train-locations", description = "Train locations")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "train-locations.geojson")
public class TrainLocationGeoJsonController extends ADataController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationController trainLocationController;

    @Autowired
    private GeoJsonFormatter geoJsonFormatter;
    private Function<TrainLocation, Double[]> converter = s -> new Double[]{s.location.getX(), s.location.getY()};

    @Operation(summary = "Returns latest wsg84 coordinates for trains in geojson format")
    @RequestMapping(method = RequestMethod.GET, path = "latest", produces = "application/vnd.geo+json")
    public FeatureCollection getTrainLocationsAsGeoJson(
            @RequestParam(required = false) @Parameter(example = "1,1,70,70", description = "bbox") List<Double> bbox,
            HttpServletResponse response) {
        validateBBox(bbox);
        List<TrainLocation> trainLocations = trainLocationController.getTrainLocations(bbox, response);
        return geoJsonFormatter.wrapAsGeoJson(trainLocations, converter);
    }

    @Operation(summary = "Returns latest wsg84 coordinates for a train in geojson format")
    @RequestMapping(method = RequestMethod.GET, path = "latest/{train_number}", produces = "application/vnd.geo+json")
    public FeatureCollection getTrainLocationByTrainNumberAsGeoJson(
            @PathVariable @Parameter(example = "1", description = "train_number") Long train_number,
            @RequestParam(required = false) @Parameter(example = "1,1,70,70", description = "bbox") List<Double> bbox,
            HttpServletResponse response) {
        validateBBox(bbox);
        List<TrainLocation> trainLocations = trainLocationController.getTrainLocationByTrainNumber(train_number, bbox, response);
        return geoJsonFormatter.wrapAsGeoJson(trainLocations, converter);
    }

    @Operation(summary = "Returns wsg84 coordinates for a train run on departure date in geojson format")
    @RequestMapping(method = RequestMethod.GET, path = "{departure_date}/{train_number}", produces = "application/vnd.geo+json")
    public FeatureCollection getTrainLocationByTrainNumberAndDepartureDateAsGeoJson(
            @PathVariable @Parameter(example = "1") Long train_number,
            @PathVariable @Parameter(description = "departure_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
            @RequestParam(required = false) @Parameter(example = "1,1,70,70", description = "bbox") List<Double> bbox,
            HttpServletResponse response) {
        validateBBox(bbox);
        List<TrainLocation> trainLocations = trainLocationController.getTrainLocationByTrainNumberAndDepartureDate(train_number, departure_date, bbox, response);
        return geoJsonFormatter.wrapAsGeoJson(trainLocations, converter);
    }

    private void validateBBox(List<Double> bbox) {
        if (bbox != null && bbox.size() != 4) {
            throw new IllegalArgumentException("Invalid bbox");
        }
    }
}
