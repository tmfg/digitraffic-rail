package fi.livi.rata.avoindata.server.controller.api;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView.ScheduleTrains;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.exception.EndDateBeforeStartDateException;
import fi.livi.rata.avoindata.server.controller.api.exception.TooLongPeriodRequestedException;
import fi.livi.rata.avoindata.server.controller.api.exception.TrainNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

// Tag has same name with a tag in LiveTrainController.
// Don't add a description to this one or the tag will appear twice in OpenAPI definitions.
@Tag(name = "live-trains")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "live-trains")
public class ScheduleController extends ADataController {
    private static final int MAX_ROUTE_SEARCH_RESULT_SIZE = 1000;
    public static final int DAYS_BETWEEN_LIMIT = 2;

    @Autowired
    private TrainRepository trainRepository;

    @Operation(summary = "Return trains that run from {arrival_station} to {departure_station}", ignoreJsonView = true)
    @JsonView(TrainJsonView.LiveTrains.class)
    @RequestMapping(path = "station/{departure_station}/{arrival_station}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<Train> getTrainsFromDepartureToArrivalStation(
            @Parameter(description = "departure_station") @PathVariable String departure_station,
            @Parameter(description = "arrival_station") @PathVariable String arrival_station,
            @Parameter(description = "departure_date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
            @Parameter(description = "include_nonstopping") @RequestParam(required = false, defaultValue = "false") final Boolean include_nonstopping,
            @Parameter(description = "startDate") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime startDate,
            @Parameter(description = "endDate") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final ZonedDateTime endDate,
            @Parameter(description = "limit") @RequestParam(required = false) Integer limit, final HttpServletResponse response) {
        if (limit == null) {
            limit = MAX_ROUTE_SEARCH_RESULT_SIZE;
        }

        departure_station = departure_station.toUpperCase();
        arrival_station = arrival_station.toUpperCase();

        if (startDate != null && endDate != null) {
            final int daysBetween = (int) Duration.between(startDate, endDate).toDays();
            if (daysBetween > DAYS_BETWEEN_LIMIT) {
                throw new TooLongPeriodRequestedException(DAYS_BETWEEN_LIMIT, daysBetween);
            }
        }

        final List<Train> list = findTrains(departure_station, arrival_station, departure_date, include_nonstopping, startDate, endDate);

        CacheConfig.SCHEDULE_STATION_CACHECONTROL.setCacheParameter(response, list, -1);

        if (list.isEmpty() && departure_date != null) {
            throw new TrainNotFoundException(arrival_station, departure_station, departure_date);
        } else if (list.isEmpty() && departure_date == null) {
            throw new TrainNotFoundException(arrival_station, departure_station, startDate, endDate, limit);
        }

        sortByDepartureStationScheduledTime(departure_station, list);

        final Integer sublistSize = Ordering.<Integer>natural().min(limit, list.size(), MAX_ROUTE_SEARCH_RESULT_SIZE);

        return list.subList(0, sublistSize);
    }

    private List<Train> findTrains(final String departure_station, final String arrival_station, final LocalDate departure_date,
                                   final Boolean include_nonstopping, final ZonedDateTime from, final ZonedDateTime to) {
        final ZonedDateTime actualTrainStart;
        final ZonedDateTime actualTrainEnd;
        final LocalDate departureDateStart;
        final LocalDate departureDateEnd;
        if (departure_date != null) {
            actualTrainStart = departure_date.minusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki"));
            actualTrainEnd = departure_date.plusDays(2).atStartOfDay(ZoneId.of("Europe/Helsinki"));
            departureDateStart = departure_date;
            departureDateEnd = departure_date;
        } else {
            if (from == null && to == null) {
                actualTrainStart = ZonedDateTime.now();
                actualTrainEnd = actualTrainStart.plusHours(24);
            } else if (from != null && to == null) {
                actualTrainStart = from;
                actualTrainEnd = actualTrainStart.plusHours(24);
            } else if (from != null && to != null) {
                if (to.isBefore(from)) {
                    throw new EndDateBeforeStartDateException("from date is earlier than to date.");
                }
                actualTrainStart = from;
                actualTrainEnd = to;
            } else {
                throw new EndDateBeforeStartDateException("to gate given, but from date not given");
            }

            departureDateStart = actualTrainStart.minusDays(1).toLocalDate();
            departureDateEnd = actualTrainEnd.plusDays(1).toLocalDate();
        }

        final List<Train> list = trainRepository.findByStationsAndScheduledDate(departure_station, TimeTableRow.TimeTableRowType.DEPARTURE,
                arrival_station, TimeTableRow.TimeTableRowType.ARRIVAL, actualTrainStart, actualTrainEnd, departureDateStart,
                departureDateEnd, !include_nonstopping);
        return list;
    }

    private void sortByDepartureStationScheduledTime(final String departure_station, final List<Train> trains) {
        Collections.sort(trains, (firstTrain, secondTrain) -> {
            final Predicate<TimeTableRow> stationShortCodePredicate = s -> s.station.stationShortCode.equals(departure_station);
            final TimeTableRow departureTimeTableRowFirst = Iterables.find(firstTrain.timeTableRows, stationShortCodePredicate);
            final TimeTableRow departureTimeTableRowSecond = Iterables.find(secondTrain.timeTableRows, stationShortCodePredicate);

            return departureTimeTableRowFirst.scheduledTime.compareTo(departureTimeTableRowSecond.scheduledTime);
        });
    }
}
