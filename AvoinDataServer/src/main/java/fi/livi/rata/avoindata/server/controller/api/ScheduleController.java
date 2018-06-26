package fi.livi.rata.avoindata.server.controller.api;

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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@Api(tags = "live-trains", description = "Returns trains")
@RequestMapping(WebConfig.CONTEXT_PATH + "live-trains")
@Transactional(timeout = 30, readOnly = true)
public class ScheduleController extends ADataController {
    private static final int MAX_ROUTE_SEARCH_RESULT_SIZE = 1000;
    public static final int DAYS_BETWEEN_LIMIT = 2;

    @Autowired
    private TrainRepository trainRepository;

    @ApiOperation("Return trains that run from {arrival_station} to {departure_station}")
    @JsonView(ScheduleTrains.class)
    @RequestMapping(path = "station/{departure_station}/{arrival_station}", method = RequestMethod.GET)
    public List<Train> getTrainsFromDepartureToArrivalStation(@PathVariable  String departure_station,
            @PathVariable  String arrival_station,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date,
            @RequestParam(required = false, defaultValue = "false") Boolean include_nonstopping,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(required = false) Integer limit, HttpServletResponse response) {
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

        List<Train> list = findSchedules(departure_station, arrival_station, departure_date, include_nonstopping, startDate, endDate);

        if (list.isEmpty() && departure_date != null) {
            throw new TrainNotFoundException(arrival_station, departure_station, departure_date);
        } else if (list.isEmpty() && departure_date == null) {
            throw new TrainNotFoundException(arrival_station, departure_station, startDate, endDate, limit);
        }

        sortByDepartureStationScheduledTime(departure_station, list);

        final Integer sublistSize = Ordering.<Integer>natural().min(limit, list.size(), MAX_ROUTE_SEARCH_RESULT_SIZE);

        CacheConfig.SCHEDULE_STATION_CACHECONTROL.setCacheParameter(response, list.subList(0, sublistSize), -1);

        return list.subList(0, sublistSize);
    }

    private List<Train> findSchedules(final String departure_station, final String arrival_station, final LocalDate departure_date,
            final Boolean include_nonstopping, final ZonedDateTime from, final ZonedDateTime to) {
        ZonedDateTime actualScheduleStart;
        ZonedDateTime actualScheduleEnd;
        LocalDate departureDateStart;
        LocalDate departureDateEnd;
        if (departure_date != null) {
            actualScheduleStart = departure_date.minusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki"));
            actualScheduleEnd = departure_date.plusDays(2).atStartOfDay(ZoneId.of("Europe/Helsinki"));
            departureDateStart = departure_date;
            departureDateEnd = departure_date;
        } else {
            if (from == null && to == null) {
                actualScheduleStart = ZonedDateTime.now();
                actualScheduleEnd = actualScheduleStart.plusHours(24);
            } else if (from != null && to == null) {
                actualScheduleStart = from;
                actualScheduleEnd = actualScheduleStart.plusHours(24);
            } else if (from != null && to != null) {
                if (to.isBefore(from)) {
                    throw new EndDateBeforeStartDateException("from date is earlier than to date.");
                }
                actualScheduleStart = from;
                actualScheduleEnd = to;
            } else {
                throw new EndDateBeforeStartDateException("to gate given, but from date not given");
            }

            departureDateStart = actualScheduleStart.minusDays(1).toLocalDate();
            departureDateEnd = actualScheduleEnd.plusDays(1).toLocalDate();
        }

        List<Train> list = trainRepository.findByStationsAndScheduledDate(departure_station, TimeTableRow.TimeTableRowType.DEPARTURE,
                arrival_station, TimeTableRow.TimeTableRowType.ARRIVAL, actualScheduleStart, actualScheduleEnd, departureDateStart,
                departureDateEnd, !include_nonstopping);
        return list;
    }

    private void sortByDepartureStationScheduledTime(final String departure_station, final List<Train> trains) {
        Collections.sort(trains, (firstTrain, secondTrain) -> {
            final Predicate<TimeTableRow> stationShortCodePredicate = s -> s.station.stationShortCode.equals(departure_station);
            TimeTableRow departureTimeTableRowFirst = Iterables.find(firstTrain.timeTableRows, stationShortCodePredicate);
            TimeTableRow departureTimeTableRowSecond = Iterables.find(secondTrain.timeTableRows, stationShortCodePredicate);

            return departureTimeTableRowFirst.scheduledTime.compareTo(departureTimeTableRowSecond.scheduledTime);
        });
    }
}