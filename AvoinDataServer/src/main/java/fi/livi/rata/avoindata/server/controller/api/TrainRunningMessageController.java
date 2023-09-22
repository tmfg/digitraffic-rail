package fi.livi.rata.avoindata.server.controller.api;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "train-tracking", description = "Returns detailed information about train's location")
@RestController

@RequestMapping(WebConfig.CONTEXT_PATH + "train-tracking")
public class TrainRunningMessageController extends ADataController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    public static final int TIMESTAMP_HOUR_CONSTRAINT = 16;
    public static final int MAX_LIMIT = 1000;

    private final CacheControl cacheControl = CacheConfig.TRAIN_RUNNING_MESSAGE_CACHECONTROL;

    @Autowired
    private TrainRunningMessageRepository trainRunningMessageRepository;

    private ZonedDateTime[] getNullDepartureDateInterval(LocalDate localDate) {
        ZonedDateTime start = localDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki"));
        ZonedDateTime end = start.plusHours(TIMESTAMP_HOUR_CONSTRAINT);
        return new ZonedDateTime[]{start, end};
    }

    @Operation(summary = "Returns train running messages for single train")
    @RequestMapping(path = "/{departure_date}/{train_number}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<TrainRunningMessage> getTrainTrackingByTrainNumberAndDepartureDate(HttpServletResponse response, @PathVariable final String train_number,
                                                                                   @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
                                                                                   @RequestParam(required = false, defaultValue = "0") final Long version) {

        if (departure_date == null) {
            departure_date = trainRunningMessageRepository.getMaxDepartureDateForTrainNumber(train_number, LocalDate.now().minusDays(2));
            if (departure_date == null) {
                departure_date = LocalDate.now();
            }
        }

        ZonedDateTime[] nullDepartureDateInterval = getNullDepartureDateInterval(departure_date);
        final List<TrainRunningMessage> trainRunningMessages = trainRunningMessageRepository.findByTrainNumberAndDepartureDate(train_number,
                departure_date, version, nullDepartureDateInterval[0].toLocalDate(), nullDepartureDateInterval[0],
                nullDepartureDateInterval[1]);

        cacheControl.setCacheParameter(response, trainRunningMessages, version);

        return trainRunningMessages;
    }

    @Operation(summary = "Returns latest train running messages for single train")
    @RequestMapping(path = "/latest/{train_number}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<TrainRunningMessage> getTrainTrackingByTrainNumber(HttpServletResponse response, @PathVariable final String train_number,
                                                                   @RequestParam(required = false, defaultValue = "0") final Long version) {
        LocalDate departure_date = trainRunningMessageRepository.getMaxDepartureDateForTrainNumber(train_number, LocalDate.now().minusDays(2));
        if (departure_date == null) {
            departure_date = LocalDate.now();
        }


        return this.getTrainTrackingByTrainNumberAndDepartureDate(response, train_number, departure_date, version);
    }

    @Operation(summary = "Returns train running messages for trains that have passed {station} on {departure_date}")
    @RequestMapping(method = RequestMethod.GET, path = "station/{station}/{departure_date}")
    @Transactional(timeout = 30, readOnly = true)
    public List<TrainRunningMessage> getTrainTrackingByStationAndDepartureDate(HttpServletResponse response,
                                                                               @PathVariable final String station, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        ZonedDateTime[] nullDepartureDateInterval = getNullDepartureDateInterval(departure_date);
        List<TrainRunningMessage> trainRunningMessages = trainRunningMessageRepository.findByStationAndDepartureDate(station,
                departure_date, nullDepartureDateInterval[0].toLocalDate(), nullDepartureDateInterval[0], nullDepartureDateInterval[1]);

        cacheControl.setCacheParameter(response, trainRunningMessages, -1);
        return trainRunningMessages;
    }

    @Operation(summary = "Returns train running messages for trains that have passed {station}, {track_section} on {departure_date}")
    @RequestMapping(path = "station/{station}/{departure_date}/{track_section}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<TrainRunningMessage> getTrainTrackingByStationAndTrackSectionAndDate(HttpServletResponse response,
                                                                                     @PathVariable final String station, @PathVariable final String track_section,
                                                                                     @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        ZonedDateTime[] nullDepartureDateInterval = getNullDepartureDateInterval(departure_date);
        List<TrainRunningMessage> trainRunningMessages = trainRunningMessageRepository.findByStationAndTrackSectionAndDepartureDate(station,
                track_section, departure_date, nullDepartureDateInterval[0].toLocalDate(), nullDepartureDateInterval[0],
                nullDepartureDateInterval[1]);

        cacheControl.setCacheParameter(response, trainRunningMessages, -1);
        return trainRunningMessages;
    }

    @Operation(summary = "Returns train running messages for trains that have passed {station}, {track_section}")
    @RequestMapping(path = "/station/{station}/latest/{track_section}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<TrainRunningMessage> getTrainTrackingByStationAndTrackSectionAndLimit(HttpServletResponse response,
                                                                                      @PathVariable final String station, @PathVariable final String track_section,
                                                                                      @RequestParam(defaultValue = "100") Integer limit) {
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }

        List<TrainRunningMessage> trainRunningMessages = trainRunningMessageRepository.findByStationAndTrackSection(station, track_section,
                PageRequest.of(0, limit));

        cacheControl.setCacheParameter(response, trainRunningMessages, -1);
        return trainRunningMessages;
    }

    @Operation(summary = "Returns train running messages newer than {version}")
    @RequestMapping(path = "/", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<TrainRunningMessage> getTrainTrackingByVersion(final HttpServletResponse response,
                                                               @RequestParam(required = false) Long version) {
        if (version == null) {
            version = trainRunningMessageRepository.getMaxVersion() - 1;
        }

        final List<TrainRunningMessage> items = trainRunningMessageRepository.findByVersionGreaterThan(version, PageRequest.of(0, 2500));

        cacheControl.setCacheParameter(response, items, version);
        return items;
    }
}
