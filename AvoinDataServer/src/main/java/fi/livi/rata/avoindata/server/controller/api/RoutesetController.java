package fi.livi.rata.avoindata.server.controller.api;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "routeset")
@Transactional(timeout = 30, readOnly = true)
public class RoutesetController extends ADataController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    public static final int TIMESTAMP_HOUR_CONSTRAINT = 16;

    private final CacheControl cacheControl = CacheConfig.TRAIN_RUNNING_MESSAGE_CACHECONTROL;

    @Autowired
    private RoutesetRepository routesetRepository;

    private ZonedDateTime[] getNullDepartureDateInterval(LocalDate localDate) {
        ZonedDateTime start = localDate.plusDays(1).atStartOfDay(ZoneId.of("Europe/Helsinki"));
        ZonedDateTime end = start.plusHours(TIMESTAMP_HOUR_CONSTRAINT);
        return new ZonedDateTime[]{start, end};
    }

    @RequestMapping("{train_number}")
    public List<Routeset> getByTrainNumber(HttpServletResponse response, @PathVariable final String train_number,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        if (departure_date == null) {
            departure_date = routesetRepository.getMaxDepartureDateForTrainNumber(train_number, LocalDate.now().minusDays(2));
            if (departure_date == null) {
                departure_date = LocalDate.now();
            }
        }

        ZonedDateTime[] nullDepartureDateInterval = getNullDepartureDateInterval(departure_date);
        final List<Routeset> trainRunningMessages = routesetRepository.findByTrainNumberAndDepartureDate(train_number,
                departure_date, -1L, nullDepartureDateInterval[0].toLocalDate(), nullDepartureDateInterval[0],
                nullDepartureDateInterval[1]);

        cacheControl.setCacheParameter(response, trainRunningMessages, -1);

        return trainRunningMessages;
    }

    @RequestMapping(path = "/station/{station}")
    public List<Routeset> getByStationAndDepartureDate(HttpServletResponse response, @PathVariable final String station,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        ZonedDateTime[] nullDepartureDateInterval = getNullDepartureDateInterval(departure_date);
        List<Routeset> trainRunningMessages = routesetRepository.findByStationAndDepartureDate(station,
                departure_date, nullDepartureDateInterval[0].toLocalDate(), nullDepartureDateInterval[0], nullDepartureDateInterval[1]);

        cacheControl.setCacheParameter(response, trainRunningMessages, -1);
        return trainRunningMessages;
    }

    @RequestMapping
    public List<Routeset> getByVersion(final HttpServletResponse response,
            @RequestParam(required = false, defaultValue = "0") Long version) {

        final List<Routeset> items = routesetRepository.findByVersionGreaterThan(version, new PageRequest(0, 2500));

        cacheControl.setCacheParameter(response, items, version);
        return items;
    }
}
