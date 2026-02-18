package fi.livi.rata.avoindata.server.controller.api;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import fi.livi.rata.avoindata.server.controller.api.history.HistoryDto;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.dao.train.AllTrainsRepository;
import fi.livi.rata.avoindata.common.dao.train.FindByTrainIdService;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

// Tag has same name with a tag in GtfsController.
// Don't add a description to this one or the tag will appear twice in OpenAPI definitions.
@Tag(name = "trains")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "trains")
public class TrainController extends ADataController {
    public static int MAX_ANNOUNCED_TRAINS = 2500;

    private final TrainRepository trainRepository;
    private final AllTrainsRepository allTrainsRepository;
    private final BatchExecutionService bes;
    private final FindByTrainIdService findByTrainIdService;

    private final CacheControl forAllLiveTrains = CacheConfig.LIVE_TRAIN_ALL_TRAINS_CACHECONTROL;
    private final CacheControl forSingleLiveTrains = CacheConfig.LIVE_TRAIN_SINGLE_TRAIN_CACHECONTROL;

    public TrainController(final TrainRepository trainRepository, final AllTrainsRepository allTrainsRepository, final BatchExecutionService bes, final FindByTrainIdService findByTrainIdService) {
        this.trainRepository = trainRepository;
        this.allTrainsRepository = allTrainsRepository;
        this.bes = bes;
        this.findByTrainIdService = findByTrainIdService;
    }

    @Operation(summary = "Returns trains that are newer than {version}",
               ignoreJsonView = true)
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(method = RequestMethod.GET,
                    path = "")
    @Transactional(readOnly = true)
    public List<Train> getTrainsByVersion(
            @RequestParam(required = false)
            Long version,
            final HttpServletResponse response) {
        if (version == null) {
            version = allTrainsRepository.getMaxVersion() - 1;
        }

        final List<AllTrainsRepository.FindByVersionQueryResult> results =
                findByTrainIdService.getTrainsGreaterThanVersionRecursive(version, MAX_ANNOUNCED_TRAINS);

        final List<TrainId> trainIds = createTrainIdsFromRawIds(results);

        final List<Train> trains =
                trainIds.isEmpty() ? Collections.emptyList() : bes.mapAndSort(findByTrainIdService::findAllTrainsByIds, trainIds, Train::compareTo);

        forAllLiveTrains.setCacheParameter(response, trains, version);

        return trains;
    }

    private List<TrainId> createTrainIdsFromRawIds(final List<AllTrainsRepository.FindByVersionQueryResult> rawIds) {
        return rawIds.stream().map(s -> new TrainId(s.getTrain_number(), s.getDeparture_date())).collect(Collectors.toList());
    }

    @Operation(summary = "Returns latest train",
               ignoreJsonView = true)
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(value = "/latest/{train_number}",
                    method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public List<Train> getTrainByTrainNumber(
            @PathVariable
            final long train_number,
            @RequestParam(required = false,
                          defaultValue = "0")
            final long version,
            final HttpServletResponse response) {
        return this.getTrainByTrainNumberAndDepartureDate(train_number, null, false, version, response);
    }

    @Operation(summary = "Returns a specific train",
               ignoreJsonView = true)
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(value = "/{departure_date}/{train_number}",
                    method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public List<Train> getTrainByTrainNumberAndDepartureDate(
            @PathVariable
            final long train_number,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            final LocalDate departure_date,
            @RequestParam(required = false,
                          defaultValue = "false")
            final boolean include_deleted,
            @RequestParam(required = false,
                          defaultValue = "0")
            final long version,
            final HttpServletResponse response) {

        List<Train> trains = new ArrayList<>();

        if (departure_date == null) {
            trains = getTrainWithoutDepartureDate(train_number, version, include_deleted);
        } else {
            final Train train = trainRepository.findByDepartureDateAndTrainNumber(departure_date, train_number, include_deleted);
            if (train != null) {
                trains = List.of(train);
            }
        }

        forSingleLiveTrains.setCacheParameter(response, trains, version);

        return trains;
    }

    @Operation(summary = "Returns trains run on {departure_date}",
               ignoreJsonView = true,
               responses = {
                       @ApiResponse(responseCode = "200",
                                    content = @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Train.class)))) })
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(method = RequestMethod.GET,
                    path = "/{departure_date}")
    @Transactional(readOnly = true)
    public List<Train> getTrainsByDepartureDate(
            @PathVariable("departure_date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            final LocalDate departureDate,
            @RequestParam(name = "include_deleted",
                          required = false,
                          defaultValue = "false")
            final boolean includeDeleted,
            final HttpServletResponse response) {

        final List<TrainId> trainIds = trainRepository.findTrainIdByDepartureDate(departureDate);

        final List<Train> trainsResponse;
        if (!trainIds.isEmpty()) {
            if (includeDeleted) {
                trainsResponse = bes.mapAndSort(findByTrainIdService::findTrainsIncludeDeleted, trainIds, Train::compareTo);
            } else {
                trainsResponse = bes.mapAndSort(findByTrainIdService::findTrains, trainIds, Train::compareTo);
            }
        } else {
            trainsResponse = Lists.newArrayList();
        }

        CacheControl.addHistoryCacheParametersForDailyResult(departureDate, response);

        return trainsResponse;
    }

    /**
     * This is a fake method that does not actually implement this call.  The implementation
     * is in train-history-backend and faking the swagger is the easiest path.
     */
    @Operation(summary = "Returns all train versions for given train.  History is available for 14 days.")
    @RequestMapping(method = RequestMethod.GET, path = "history/{departure_date}/{train_number}")
    public List<HistoryDto> getTrainHistory(
            @Parameter(description = "Departure date") @PathVariable("departure_date") final LocalDate departureDate,
            @Parameter(description = "Train number") @PathVariable("train_number") final int trainNumber) {
        throw new NotImplementedException();
    }

    private List<Train> getTrainWithoutDepartureDate(final long trainNumber,
                                                     final long version,
                                                     final boolean includeDeleted) {
        final List<Object[]> liveTrains = trainRepository.findLiveTrainByTrainNumber(trainNumber);
        final List<TrainId> trainsToRetrieve = extractNewerTrainIds(version, liveTrains);

        if (!trainsToRetrieve.isEmpty()) {
            return includeDeleted ? findByTrainIdService.findTrainsIncludeDeleted(trainsToRetrieve) :
                   findByTrainIdService.findTrains(trainsToRetrieve);
        }

        return Collections.emptyList();
    }

    private List<TrainId> extractNewerTrainIds(final long version, final List<Object[]> liveTrains) {
        return liveTrains.stream().filter(train -> (Long) train[3] > version).map(tuple -> {
            final LocalDate departureDate = LocalDate.from(((Date) tuple[1]).toLocalDate());
            final Long trainNumber = (Long) tuple[2];
            return new TrainId(trainNumber, departureDate);
        }).collect(Collectors.toList());
    }
}
