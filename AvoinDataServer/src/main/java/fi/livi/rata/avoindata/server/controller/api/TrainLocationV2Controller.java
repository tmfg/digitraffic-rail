package fi.livi.rata.avoindata.server.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Api(tags = "train-locations", description = "Train locations", position = Integer.MIN_VALUE)
@RestController
@RequestMapping("/api/v2/" + "train-locations")
public class TrainLocationV2Controller extends ADataController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationController trainLocationController;

    @Autowired
    private ObjectMapper objectMapper;

    @ApiOperation("Returns latest wsg84 coordinates for trains")
    @RequestMapping(method = RequestMethod.GET, path = "latest")
    public List<Map<String, Object>> getTrainLocationsV2(@RequestParam(required = false) @ApiParam(example = "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        List<TrainLocation> originalResponse = trainLocationController.getTrainLocations(bbox, response);

        return convertGeoJsonToSimpleJson(originalResponse);
    }

    @ApiOperation("Returns latest wsg84 coordinates for a train")
    @RequestMapping(method = RequestMethod.GET, path = "latest/{train_number}")
    public List<Map<String, Object>> getTrainLocationByTrainNumberV2(@PathVariable @ApiParam(example = "1") Long train_number, @RequestParam(required = false) @ApiParam(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        Iterable<TrainLocation> originalResponse = trainLocationController.getTrainLocationByTrainNumber(train_number, bbox, response);

        return convertGeoJsonToSimpleJson(originalResponse);
    }

    @ApiOperation("Returns wsg84 coordinates for a train run on departure date")
    @RequestMapping(method = RequestMethod.GET, path = "{departure_date}/{train_number}")
    public List<Map<String, Object>> getTrainLocationByTrainNumberAndDepartureDateV2(@PathVariable @ApiParam(example = "1") Long train_number, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date, @RequestParam(required = false) @ApiParam(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        Iterable<TrainLocation> originalResponse = trainLocationController.getTrainLocationByTrainNumberAndDepartureDate(train_number, departure_date, bbox, response);

        return convertGeoJsonToSimpleJson(originalResponse);
    }

    public List<Map<String, Object>> convertGeoJsonToSimpleJson(Iterable<TrainLocation> trainLocations) {
        List<Map<String, Object>> output = new ArrayList<>();

        for (TrainLocation trainLocation : trainLocations) {
            Map trainLocationMap = objectMapper.convertValue(trainLocation, Map.class);

            Map oldLocation = (Map) trainLocationMap.get("location");
            List oldCoordinates = (List) oldLocation.get("coordinates");

            trainLocationMap.put("location", new Double[]{(Double) oldCoordinates.get(0), (Double) oldCoordinates.get(1)});

            output.add(trainLocationMap);
        }

        return output;
    }
}
