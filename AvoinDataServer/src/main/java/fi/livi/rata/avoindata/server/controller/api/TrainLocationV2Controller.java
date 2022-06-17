package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.server.dto.TrainLocationV2;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "train-locations", description = "Train locations")
@Hidden
@RestController
@RequestMapping("/api/v2/" + "train-locations")
public class TrainLocationV2Controller extends ADataController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationController trainLocationController;

    @ApiOperation(value = "Returns latest wsg84 coordinates for trains", response = Object.class)
    @RequestMapping(method = RequestMethod.GET, path = "latest")
    public List<TrainLocationV2> getTrainLocationsV2(@RequestParam(required = false) List<Double> bbox, HttpServletResponse response) {
        List<TrainLocation> originalResponse = trainLocationController.getTrainLocations(bbox, response);

        return convertGeoJsonToSimpleJson(originalResponse);
    }

    @ApiOperation("Returns latest wsg84 coordinates for a train")
    @RequestMapping(method = RequestMethod.GET, path = "latest/{train_number}")
    public List<TrainLocationV2> getTrainLocationByTrainNumberV2(@PathVariable @ApiParam(example = "1") Long train_number, @RequestParam(required = false) @ApiParam(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        Iterable<TrainLocation> originalResponse = trainLocationController.getTrainLocationByTrainNumber(train_number, bbox, response);

        return convertGeoJsonToSimpleJson(originalResponse);
    }

    @ApiOperation("Returns wsg84 coordinates for a train run on departure date")
    @RequestMapping(method = RequestMethod.GET, path = "{departure_date}/{train_number}")
    public List<TrainLocationV2> getTrainLocationByTrainNumberAndDepartureDateV2(@PathVariable @ApiParam(example = "1") Long train_number, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date, @RequestParam(required = false) @ApiParam(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        Iterable<TrainLocation> originalResponse = trainLocationController.getTrainLocationByTrainNumberAndDepartureDate(train_number, departure_date, bbox, response);

        return convertGeoJsonToSimpleJson(originalResponse);
    }

    public List<TrainLocationV2> convertGeoJsonToSimpleJson(Iterable<TrainLocation> trainLocations) {
        List<TrainLocationV2> output = new ArrayList<>();

        for (TrainLocation trainLocation : trainLocations) {
            output.add(new TrainLocationV2(trainLocation));
        }

        return output;
    }
}
