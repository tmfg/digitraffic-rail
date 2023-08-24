package fi.livi.rata.avoindata.server.controller.api;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.locationtech.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "train-locations", description = "Train locations")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "train-locations")
public class TrainLocationController extends ADataController {
    public static final int CACHE_MAX_AGE = 1;
    public static final int CACHE_MAX_AGE_HISTORY = 15;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationRepository trainLocationRepository;
    @Autowired
    private DateProvider dateProvider;

    @Operation(summary = "Returns latest wsg84 coordinates for trains")
    @RequestMapping(method = RequestMethod.GET, path = "latest")
    public List<TrainLocation> getTrainLocations(@RequestParam(required = false) @Parameter(example = "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE);

        final List<Long> ids = trainLocationRepository.findLatest(dateProvider.nowInHelsinki().minusMinutes(15));
        return getAndFilterTrainLocations(bbox, response, ids);
    }

    @Operation(summary = "Returns latest wsg84 coordinates for a train")
    @RequestMapping(method = RequestMethod.GET, path = "latest/{train_number}")
    public List<TrainLocation> getTrainLocationByTrainNumber(@PathVariable @Parameter(example = "1") Long train_number, @RequestParam(required = false) @Parameter(example =
            "1,1,70,70") List<Double> bbox, HttpServletResponse response) {
        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE);

        final List<Long> ids = trainLocationRepository.findLatestForATrain(dateProvider.nowInHelsinki().minusMinutes(15), train_number);
        return getAndFilterTrainLocations(bbox, response, ids);
    }

    @Operation(summary = "Returns wsg84 coordinates for a train run on departure date")
    @RequestMapping(method = RequestMethod.GET, path = "{departure_date}/{train_number}")
    public List<TrainLocation> getTrainLocationByTrainNumberAndDepartureDate(
            @PathVariable @Parameter(example = "1") Long train_number,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
            @RequestParam(required = false) @Parameter(example = "1,1,70,70") List<Double> bbox,
            HttpServletResponse response) {

        CacheControl.setCacheMaxAgeSeconds(response, CACHE_MAX_AGE_HISTORY);

        final List<TrainLocation> trainLocations = trainLocationRepository.findTrain(train_number, departure_date);
        return filterByBbox(bbox, response, trainLocations);
    }

    private List<TrainLocation> getAndFilterTrainLocations(List<Double> bbox, final HttpServletResponse response, final List<Long> ids) {
        // Shortcut
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        final List<TrainLocation> result = trainLocationRepository.findAllOrderByTrainNumber(ids);

        return filterByBbox(bbox, response, result);
    }

    private List<TrainLocation> filterByBbox(List<Double> bbox, final HttpServletResponse response, final List<TrainLocation> result) {
        if (bbox != null) {
            if (bbox.size() != 4) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
            return Lists.newArrayList(Iterables.filter(result, tl -> isInsideBoundingBox(tl, bbox)));
        } else {
            return result;
        }
    }

    private boolean isInsideBoundingBox(TrainLocation trainLocation, List<Double> boundingBox) {
        final Point location = trainLocation.location;

        final Double x1 = boundingBox.get(0);
        final Double y1 = boundingBox.get(1);
        final Double x2 = boundingBox.get(2);
        final Double y2 = boundingBox.get(3);

        final double locationX = location.getX();
        final double locationY = location.getY();

        return locationX >= x1 && locationY >= y1 && locationX <= x2 && locationY <= y2;
    }
}
