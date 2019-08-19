package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.geojson.GeoJsonResponse;
import fi.livi.rata.avoindata.server.services.GeoJsonFormatter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Api(tags = "train-locations", description = "Train locations", position = Integer.MIN_VALUE)
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "train-locations.geojson")
public class TrainLocationGeoJsonController extends ADataController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationController trainLocationController;

    @Autowired
    private GeoJsonFormatter geoJsonFormatter;
    private Function<TrainLocation, Double[]> converter = s -> new Double[]{s.location.getX(), s.location.getY()};

    @ApiOperation("Returns latest wsg84 coordinates for trains in geojson format")
    @RequestMapping(method = RequestMethod.GET, path = "latest")
    public GeoJsonResponse getTrainLocations(@RequestParam(required = false) @ApiParam(example = "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        List<TrainLocation> trainLocations = trainLocationController.getTrainLocations(bbox, response);
        return geoJsonFormatter.wrapAsGeoJson(response, trainLocations, converter);
    }

    @ApiOperation("Returns latest wsg84 coordinates for a train in geojson format")
    @RequestMapping(method = RequestMethod.GET, path = "latest/{train_number}")
    public GeoJsonResponse getTrainLocationByTrainNumber(@PathVariable @ApiParam(example = "1") Long train_number, @RequestParam(required = false) @ApiParam(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        List<TrainLocation> trainLocations = trainLocationController.getTrainLocationByTrainNumber(train_number, bbox, response);
        return geoJsonFormatter.wrapAsGeoJson(response, trainLocations, converter);
    }

    @ApiOperation("Returns wsg84 coordinates for a train run on departure date in geojson format")
    @RequestMapping(method = RequestMethod.GET, path = "{departure_date}/{train_number}")
    public GeoJsonResponse getTrainLocationByTrainNumberAndDepartureDate(@PathVariable @ApiParam(example = "1") Long train_number, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date, @RequestParam(required = false) @ApiParam(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        List<TrainLocation> trainLocations = trainLocationController.getTrainLocationByTrainNumberAndDepartureDate(train_number, departure_date, bbox, response);
        return geoJsonFormatter.wrapAsGeoJson(response, trainLocations, converter);
    }
}
