package fi.livi.rata.avoindata.server.controller.api;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.server.config.WebConfig;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping(WebConfig.CONTEXT_PATH)
@ApiIgnore
public class RewriteController {
    private static final String LIVE_TRAINS_PREFIX = "live-trains";
    private static final String ALL_TRAINS_PREFIX = "all-trains";
    private static final String COMPOSITIONS_PREFIX = "compositions";
    private static final String HISTORY_PREFIX = "history";
    private static final String SCHEDULES_PREFIX = "schedules";
    private static final String TRAIN_TRACKING_PREFIX = "train-tracking";

    @Autowired
    private TrainController trainController;

    @Autowired
    private CompositionController compositionController;

    @Autowired
    private LiveTrainController liveTrainController;

    @Autowired
    private ScheduleController scheduleController;

    @Autowired
    private TrainRunningMessageController trainRunningMessageController;


    @RequestMapping(path = ALL_TRAINS_PREFIX, method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public List<Train> getAllTrains(@RequestParam(required = false) Long version, HttpServletResponse response) {
        return trainController.getTrainsByVersion(version, response);
    }

    @RequestMapping(path = COMPOSITIONS_PREFIX, method = RequestMethod.GET, params = "departure_date")
    public Collection<Composition> getCompositionsByDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date, HttpServletResponse response) {
        return compositionController.getCompositionsByDepartureDate(departure_date, response);
    }

    @RequestMapping(value = COMPOSITIONS_PREFIX + "/{train_number}", method = RequestMethod.GET, params = "departure_date")
    public Composition getCompositionByTrainNumberAndDepartureDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
            @PathVariable("train_number") Long train_number, HttpServletResponse response) {
        return compositionController.getCompositionByTrainNumberAndDepartureDate(departure_date, train_number, response);
    }

    @RequestMapping(path = HISTORY_PREFIX, method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public Stream<Train> getTrains(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
                                   HttpServletResponse response) {
        return trainController.getTrainsByDepartureDate(departure_date, false, response);
    }

    @RequestMapping(value = HISTORY_PREFIX + "/{train_number}", method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public Collection<Train> getTrains(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
                                       @PathVariable final Long train_number, HttpServletResponse response) {
        return trainController.getTrainByTrainNumberAndDepartureDate(train_number, departure_date, false, 0, response);
    }

    @RequestMapping(value = LIVE_TRAINS_PREFIX + "/{train_number}", method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public List<Train> getSingleTrain(@PathVariable final long train_number,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
                                      @RequestParam(required = false, defaultValue = "0") long version, HttpServletResponse response) {
        return trainController.getTrainByTrainNumberAndDepartureDate(train_number, departure_date, false, version, response);
    }

    @RequestMapping(path = LIVE_TRAINS_PREFIX, params = "station", method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public List<Train> getStationsTrainsLimitByNumber(@RequestParam String station,
                                                      @RequestParam(required = false, defaultValue = "0") long version,
                                                      @RequestParam(required = false, defaultValue = "5") int arrived_trains,
                                                      @RequestParam(required = false, defaultValue = "5") int arriving_trains,
                                                      @RequestParam(required = false, defaultValue = "5") int departed_trains,
                                                      @RequestParam(required = false, defaultValue = "5") int departing_trains,
                                                      @RequestParam(required = false, defaultValue = "false") Boolean include_nonstopping, HttpServletResponse response) {
        return liveTrainController.getStationsTrains(station, version, arrived_trains, arriving_trains, departed_trains,
                departing_trains, null, null, null, null, include_nonstopping, null, response);
    }

    @RequestMapping(path = LIVE_TRAINS_PREFIX, params = {"station", "minutes_before_departure", "minutes_after_departure",
            "minutes_before_arrival", "minutes_after_arrival"}, method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public List<Train> getStationsTrainsLimitByTime(@RequestParam(required = true) String station,
                                                    @RequestParam(defaultValue = "0") long version, @RequestParam int minutes_before_departure,
                                                    @RequestParam int minutes_after_departure, @RequestParam int minutes_before_arrival, @RequestParam int minutes_after_arrival,
                                                    @RequestParam(defaultValue = "false") Boolean include_nonstopping, HttpServletResponse response) {
        return liveTrainController.getStationsTrains(station, version, null, null, null, null, minutes_before_departure, minutes_after_departure,
                minutes_before_arrival, minutes_after_arrival, include_nonstopping, null, response);
    }

    @RequestMapping(path = SCHEDULES_PREFIX, method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public Stream<Train> getOldSchedules(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
                                         HttpServletResponse response) {
        return trainController.getTrainsByDepartureDate(departure_date, false, response);
    }

    @RequestMapping(value = SCHEDULES_PREFIX + "/{train_number}", method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public Train getTrain(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
                          @PathVariable final Long train_number, HttpServletResponse response) {
        final List<Train> trains = trainController.getTrainByTrainNumberAndDepartureDate(train_number, departure_date, false, 0, response);
        if (trains.isEmpty()) {
            return null;
        } else {
            return trains.get(0);
        }
    }

    @RequestMapping(value = SCHEDULES_PREFIX, params = {"departure_station", "arrival_station"}, method = RequestMethod.GET)
    @JsonView(TrainJsonView.LiveTrains.class)
    public List<Train> getTrains(@RequestParam final String departure_station, @RequestParam final String arrival_station,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
                                 @RequestParam(required = false, defaultValue = "false") Boolean include_nonstopping,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
                                 @RequestParam(required = false) Integer limit, HttpServletResponse response) {
        return scheduleController.getTrainsFromDepartureToArrivalStation(departure_station, arrival_station, departure_date,
                include_nonstopping, from, to, limit, response);
    }

    @RequestMapping(value = TRAIN_TRACKING_PREFIX, params = {"station", "departure_date"}, method = RequestMethod.GET)
    public List<TrainRunningMessage> getByStationAndDepartureDate(HttpServletResponse response, @RequestParam final String station,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {
        return trainRunningMessageController.getTrainTrackingByStationAndDepartureDate(response, station, departure_date);
    }

    @RequestMapping(value = TRAIN_TRACKING_PREFIX, params = {"station", "track_section", "departure_date"}, method = RequestMethod.GET)
    public List<TrainRunningMessage> getStationAndTrackSectionAndDate(HttpServletResponse response, @RequestParam final String station,
                                                                      @RequestParam final String track_section,
                                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {
        return trainRunningMessageController.getTrainTrackingByStationAndTrackSectionAndDate(response, station, track_section,
                departure_date);
    }

    @RequestMapping(value = TRAIN_TRACKING_PREFIX, params = {"station", "track_section"}, method = RequestMethod.GET)
    public List<TrainRunningMessage> getStationAndTrackSectionAndLimit(HttpServletResponse response, @RequestParam final String station,
                                                                       @RequestParam final String track_section, @RequestParam(defaultValue = "100") Integer limit) {
        return trainRunningMessageController.getTrainTrackingByStationAndTrackSectionAndLimit(response, station, track_section, limit);
    }

    @RequestMapping(value = TRAIN_TRACKING_PREFIX, method = RequestMethod.GET)
    public List<TrainRunningMessage> getByVersion(final HttpServletResponse response,
                                                  @RequestParam(required = false) Long version) {
        return trainRunningMessageController.getTrainTrackingByVersion(response, version);
    }

    @RequestMapping(path = TRAIN_TRACKING_PREFIX + "/{train_number}", method = RequestMethod.GET)
    public List<TrainRunningMessage> getTrainTrackingByTrainNumberAndDepartureDate(HttpServletResponse response, @PathVariable final String train_number,
                                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
                                                                                   @RequestParam(required = false, defaultValue = "0") final Long version) {
        return trainRunningMessageController.getTrainTrackingByTrainNumberAndDepartureDate(response, train_number, departure_date, version);
    }
}
