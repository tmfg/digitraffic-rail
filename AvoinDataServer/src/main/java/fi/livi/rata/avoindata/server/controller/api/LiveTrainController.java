package fi.livi.rata.avoindata.server.controller.api;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Entity;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.math.BigInteger;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Api(tags = "live-trains", description = "Returns trains that have been recently active")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "live-trains")
@XRayEnabled
public class LiveTrainController extends ADataController {
    public static final int TRAIN_FETCH_SIZE = 50;

    @Autowired
    private TrainRepository trainRepository;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${avoindataserver.livetrains.maxTrainRerieveRequest:1000}")
    private int maxTrainRetrieveRequest;

    private ExecutorService executor = Executors.newFixedThreadPool(10);

    private CacheControl forAllLiveTrains = CacheConfig.LIVE_TRAIN_ALL_TRAINS_CACHECONTROL;
    private CacheControl forStationLiveTrains = CacheConfig.LIVE_TRAIN_STATION_CACHECONTROL;
    private CacheControl forSingleLiveTrains = CacheConfig.LIVE_TRAIN_SINGLE_TRAIN_CACHECONTROL;

    @ApiOperation(value = "Returns active trains that are newer than {version}")
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(method = RequestMethod.GET)
    public List<Train> getLiveTrainsByVersion(@RequestParam(defaultValue = "0", name = "version") Long version, HttpServletResponse response) {
        List<Object[]> liveTrains = trainRepository.findLiveTrains(version, 60 * 4);
        List<TrainId> trainsToRetrieve = extractNewerTrainIds(version, liveTrains);

        List<Train> trains = new LinkedList<>();
        if (!trainsToRetrieve.isEmpty()) {
            trains = trainRepository.findTrains(trainsToRetrieve);
        }

        forAllLiveTrains.setCacheParameter(response, trains, version);

        return trains;
    }

    @JsonView(TrainJsonView.LiveTrains.class)
    @ApiOperation(value = "Returns trains that travel trough {station}", response = Train.class, responseContainer = "List")
    @RequestMapping(path = "/station/{station}", method = RequestMethod.GET)
    public List<Train> getStationsTrains(@PathVariable String station, @RequestParam(required = false, defaultValue = "0") long version,
                                         @RequestParam(required = false, defaultValue = "5") Integer arrived_trains,
                                         @RequestParam(required = false, defaultValue = "5") Integer arriving_trains,
                                         @RequestParam(required = false, defaultValue = "5") Integer departed_trains,
                                         @RequestParam(required = false, defaultValue = "5") Integer departing_trains,
                                         @RequestParam(required = false) Integer minutes_before_departure,
                                         @RequestParam(required = false) Integer minutes_after_departure,
                                         @RequestParam(required = false) Integer minutes_before_arrival,
                                         @RequestParam(required = false) Integer minutes_after_arrival,
                                         @RequestParam(required = false, defaultValue = "false") Boolean include_nonstopping, HttpServletResponse response) {
        if (minutes_after_arrival != null && minutes_after_departure != null && minutes_before_arrival != null &&
                minutes_before_departure != null) {
            return this.getLiveTrainsUsingTimeFiltering(station, version, minutes_before_departure, minutes_after_departure,
                    minutes_before_arrival, minutes_after_arrival, include_nonstopping, response);
        } else {
            return this.getLiveTrainsUsingQuantityFiltering(station, version, arrived_trains, arriving_trains, departed_trains,
                    departing_trains, include_nonstopping, response);
        }
    }

    public List<Train> getLiveTrainsUsingQuantityFiltering(String station, long version, int arrived_trains, int arriving_trains,
                                                           int departed_trains, int departing_trains, Boolean include_nonstopping, HttpServletResponse response) {
        assertParameters(arrived_trains, arriving_trains, departed_trains, departing_trains);

        List<Object[]> liveTrains = trainRepository.findLiveTrainsIds(station, departed_trains, departing_trains, arrived_trains,
                arriving_trains, !include_nonstopping);

        List<TrainId> trainsToRetrieve = extractNewerTrainIds(version, liveTrains);

        CacheControl.setCacheMaxAgeSeconds(response, forStationLiveTrains.WITHOUT_CHANGENUMBER_RESULT);

        if (!trainsToRetrieve.isEmpty()) {
            return getTrains(trainsToRetrieve);
        } else {
            return Lists.newArrayList();
        }
    }

    private List<Train> getTrains(List<TrainId> trainsToRetrieve) {
        List<Future<List<Train>>> trainFutures = new ArrayList<>();

        Entity segment = AWSXRay.getTraceEntity();

        ArrayList<TrainId> uniqueIds = Lists.newArrayList(Sets.newLinkedHashSet(trainsToRetrieve));
        for (List<TrainId> trainIds : Lists.partition(uniqueIds, TRAIN_FETCH_SIZE)) {
            Future<List<Train>> streamFuture = executor.submit(() -> {
                AWSXRay.setTraceEntity(segment);
                Subsegment subsegment = AWSXRay.beginSubsegment("## Execute train fetch future");
                List<Train> trains = trainRepository.findTrains(trainIds);
                AWSXRay.endSubsegment();
                return trains;
            });
            trainFutures.add(streamFuture);
        }

        List<Train> trains = new ArrayList<>();
        for (Future<List<Train>> trainFuture : trainFutures) {
            try {
                trains.addAll(trainFuture.get());
            } catch (Exception e) {
                log.error("Error fetching trains", e);
            }
        }

        Collections.sort(trains, Train::compareTo);

        AWSXRay.endSegment();

        return trains;
    }


    public List<Train> getLiveTrainsUsingTimeFiltering(String station, long version, Integer minutes_before_departure,
                                                       Integer minutes_after_departure, Integer minutes_before_arrival, Integer minutes_after_arrival, Boolean include_nonstopping,
                                                       HttpServletResponse response) {
        final ZonedDateTime now = ZonedDateTime.now();

        ZonedDateTime startArrival = now.minusMinutes(minutes_after_arrival);
        ZonedDateTime endArrival = now.plusMinutes(minutes_before_arrival);

        ZonedDateTime startDeparture = now.minusMinutes(minutes_after_departure);
        ZonedDateTime endDeparture = now.plusMinutes(minutes_before_departure);

        List<LiveTimeTableTrain> liveTrains = trainRepository.findLiveTrains(station, startDeparture, endDeparture, !include_nonstopping,
                version, startArrival, endArrival);

        CacheControl.setCacheMaxAgeSeconds(response, forStationLiveTrains.WITHOUT_CHANGENUMBER_RESULT);

        if (!liveTrains.isEmpty()) {
            return getTrains(Lists.transform(liveTrains, s -> s.id));
        } else {
            return Lists.newArrayList();
        }
    }

    private void assertParameters(int arrived_trains, int arriving_trains, int departed_trains, int departing_trains) {
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

    private List<TrainId> extractNewerTrainIds(long version, List<Object[]> liveTrains) {
        return liveTrains.stream().filter(train -> ((BigInteger) train[3]).longValue() > version).map(tuple -> {
            LocalDate departureDate = LocalDate.from(((Date) tuple[1]).toLocalDate());
            BigInteger trainNumber = (BigInteger) tuple[2];
            return new TrainId(trainNumber.longValue(), departureDate);
        }).collect(Collectors.toList());
    }

}
