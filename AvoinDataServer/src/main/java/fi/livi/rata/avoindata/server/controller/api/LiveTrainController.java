package fi.livi.rata.avoindata.server.controller.api;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.dao.train.FindByTrainIdService;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.LiveTimeTableTrain;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.exception.TrainLimitBelowZeroException;
import fi.livi.rata.avoindata.server.controller.api.exception.TrainMaximumLimitException;
import fi.livi.rata.avoindata.server.controller.api.exception.TrainMinimumLimitException;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import fi.livi.rata.avoindata.server.controller.utils.FindByIdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "live-trains", description = "Returns trains that have been recently active")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "live-trains")
public class LiveTrainController extends ADataController {
    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private FindByIdService findByIdService;

    @Autowired
    private FindByTrainIdService findByTrainIdService;

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @Value("${avoindataserver.livetrains.maxTrainRerieveRequest:1000}")
    private int maxTrainRetrieveRequest;

    private CacheControl forAllLiveTrains = CacheConfig.LIVE_TRAIN_ALL_TRAINS_CACHECONTROL;
    private CacheControl forStationLiveTrains = CacheConfig.LIVE_TRAIN_STATION_CACHECONTROL;
    private CacheControl forSingleLiveTrains = CacheConfig.LIVE_TRAIN_SINGLE_TRAIN_CACHECONTROL;

    @Operation(summary = "Returns active trains that are newer than {version}", ignoreJsonView = true)
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(method = RequestMethod.GET)
    public List<Train> getLiveTrainsByVersion(@Parameter(description = "version") @RequestParam(defaultValue = "0", name = "version") final Long version,
                                              final HttpServletResponse response) {
        final List<Object[]> liveTrains = trainRepository.findLiveTrains(version, 60 * 4);
        final List<TrainId> trainsToRetrieve = extractNewerTrainIds(version, liveTrains);

        final List<Train> trains = !trainsToRetrieve.isEmpty() ? findByTrainIdService.findTrains(trainsToRetrieve) : new LinkedList<>();

        forAllLiveTrains.setCacheParameter(response, trains, version);

        return trains;
    }

    @JsonView(TrainJsonView.LiveTrains.class)
    @Operation(summary = "Returns trains that travel trough {station}",
               ignoreJsonView = true,
               responses = { @ApiResponse(responseCode = "200", content = @Content(
                       mediaType = "application/json",
                       array = @ArraySchema(schema = @Schema(implementation = Train.class)))) })
    @RequestMapping(path = "/station/{station}", method = RequestMethod.GET)
    public List<Train> getStationsTrains(@Parameter(description = "station") @PathVariable final String station,
                                         @Parameter(description = "version") @RequestParam(required = false, defaultValue = "0") final long version,
                                         @Parameter(description = "arrived_trains") @RequestParam(required = false, defaultValue = "5") final Integer arrived_trains,
                                         @Parameter(description = "arriving_trains") @RequestParam(required = false, defaultValue = "5") final Integer arriving_trains,
                                         @Parameter(description = "departed_trains") @RequestParam(required = false, defaultValue = "5") final Integer departed_trains,
                                         @Parameter(description = "departing_trains") @RequestParam(required = false, defaultValue = "5") final Integer departing_trains,
                                         @Parameter(description = "minutes_before_departure") @RequestParam(required = false) final Integer minutes_before_departure,
                                         @Parameter(description = "minutes_after_departure") @RequestParam(required = false) final Integer minutes_after_departure,
                                         @Parameter(description = "minutes_before_arrival") @RequestParam(required = false) final Integer minutes_before_arrival,
                                         @Parameter(description = "minutes_after_arrival") @RequestParam(required = false) final Integer minutes_after_arrival,
                                         @Parameter(description = "include_nonstopping") @RequestParam(required = false, defaultValue = "false") final Boolean include_nonstopping,
                                         @Parameter(description = "train_categories") @RequestParam(required = false) final List<String> train_categories,
                                         HttpServletResponse response) {

        final List<Long> trainCategoryIds = getTrainCategories(train_categories);

        if (minutes_after_arrival != null && minutes_after_departure != null && minutes_before_arrival != null &&
                minutes_before_departure != null) {
            return this.getLiveTrainsUsingTimeFiltering(station, version, minutes_before_departure, minutes_after_departure,
                    minutes_before_arrival, minutes_after_arrival, include_nonstopping, trainCategoryIds, response);
        } else {
            return this.getLiveTrainsUsingQuantityFiltering(station, version, arrived_trains, arriving_trains, departed_trains,
                    departing_trains, include_nonstopping, trainCategoryIds, response);
        }
    }

    private List<Long> getTrainCategories(@RequestParam(required = false) final List<String> trainCategories) {
        final List<Long> trainCategoryIds;
        if (trainCategories == null || trainCategories.isEmpty()) {
            trainCategoryIds = Lists.transform(trainCategoryRepository.findAllCached(), s -> s.id);
        } else {
            trainCategoryIds = Lists.transform(trainCategoryRepository.findByNameCached(trainCategories), s -> s.id);
            if (trainCategoryIds.isEmpty()) {
                throw new IllegalArgumentException("No recognized train categories given");
            }
        }
        return trainCategoryIds;
    }

    public List<Train> getLiveTrainsUsingQuantityFiltering(final String station, final long version, int arrivedTrains, final int arrivingTrains,
                                                           final int departedTrains, final int departingTrains,
                                                           final Boolean includeNonstopping, final List<Long> trainCategoryIds,
                                                           final HttpServletResponse response) {
        assertParameters(arrivedTrains, arrivingTrains, departedTrains, departingTrains);

        final List<Object[]> liveTrains = trainRepository.findLiveTrainsIds(station, departedTrains, departingTrains, arrivedTrains,
                arrivingTrains, !includeNonstopping, trainCategoryIds);

        final List<TrainId> trainsToRetrieve = extractNewerTrainIds(version, liveTrains);

        CacheControl.setCacheMaxAgeSeconds(response, forStationLiveTrains.WITHOUT_CHANGENUMBER_RESULT);

        if (!trainsToRetrieve.isEmpty()) {
            return findByIdService.findById(s -> findByTrainIdService.findTrains(s), trainsToRetrieve, Train::compareTo);
        } else {
            return Lists.newArrayList();
        }
    }


    public List<Train> getLiveTrainsUsingTimeFiltering(final String station, final long version, final Integer minutesBeforeDeparture,
                                                       final Integer minutesAfterDeparture, final Integer minutesBeforeArrival,
                                                       final Integer minutesAfterArrival, final Boolean includeNonstopping,
                                                       final List<Long> trainCategoryIds, final HttpServletResponse response) {
        final ZonedDateTime now = ZonedDateTime.now();

        final ZonedDateTime startArrival = now.minusMinutes(minutesAfterArrival);
        final ZonedDateTime endArrival = now.plusMinutes(minutesBeforeArrival);

        final ZonedDateTime startDeparture = now.minusMinutes(minutesAfterDeparture);
        final ZonedDateTime endDeparture = now.plusMinutes(minutesBeforeDeparture);

        final List<LiveTimeTableTrain> liveTrains = trainRepository.findLiveTrains(station, startDeparture, endDeparture, !includeNonstopping,
                version, startArrival, endArrival, trainCategoryIds);

        CacheControl.setCacheMaxAgeSeconds(response, forStationLiveTrains.WITHOUT_CHANGENUMBER_RESULT);

        if (!liveTrains.isEmpty()) {
            final List<TrainId> trainIds = Lists.transform(liveTrains, s -> s.id);
            return findByIdService.findById(s -> findByTrainIdService.findTrains(s), trainIds, Train::compareTo);
        } else {
            return Lists.newArrayList();
        }
    }

    private void assertParameters(final int arrived_trains, final int arriving_trains, final int departed_trains, int departing_trains) {
        final int sumOfLimits = arrived_trains + arriving_trains + departed_trains + departing_trains;
        if (sumOfLimits > maxTrainRetrieveRequest) {
            throw new TrainMaximumLimitException(maxTrainRetrieveRequest);
        }

        if (arrived_trains < 0 || arriving_trains < 0 || departed_trains < 0 || departing_trains < 0) {
            throw new TrainLimitBelowZeroException();
        }

        if (sumOfLimits == 0) {
            throw new TrainMinimumLimitException();
        }
    }

    private List<TrainId> extractNewerTrainIds(final long version, final List<Object[]> liveTrains) {
        return liveTrains.stream().filter(train -> ((Long) train[3]).longValue() > version).map(tuple -> {
            final LocalDate departureDate = LocalDate.from(((Date) tuple[1]).toLocalDate());
            final Long trainNumber = (Long) tuple[2];
            return new TrainId(trainNumber.longValue(), departureDate);
        }).collect(Collectors.toList());
    }

}
