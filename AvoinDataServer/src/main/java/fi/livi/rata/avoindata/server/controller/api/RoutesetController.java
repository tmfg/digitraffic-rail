package fi.livi.rata.avoindata.server.controller.api;

import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;

@Api(tags = "routesets", description = "Returns routesets")
@RequestMapping(WebConfig.CONTEXT_PATH + "routeset")
@Transactional(timeout = 30, readOnly = true)
@RestController
public class RoutesetController extends ADataController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CacheControl cacheControl = CacheConfig.TRAIN_RUNNING_MESSAGE_CACHECONTROL;

    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private BatchExecutionService bes;

    @RequestMapping(value = "/{departure_date}/{train_number}", method = RequestMethod.GET)
    public List<Routeset> getByTrainNumber(HttpServletResponse response, @PathVariable final String train_number,
                                           @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        final List<Routeset> routesets = routesetRepository.findByTrainNumberAndDepartureDate(train_number, departure_date);

        cacheControl.setCacheParameter(response, routesets, -1);

        return routesets;
    }

    @RequestMapping(path = "station/{station}/{departure_date}", method = RequestMethod.GET)
    public List<Routeset> getRoutesetByStationAndTrackSectionAndDate(HttpServletResponse response,
                                                                     @PathVariable final String station,
                                                                     @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        List<Long> ids = routesetRepository.findIdByStationAndDepartureDate(departure_date, station);

        cacheControl.setCacheParameter(response, ids, -1);
        return findByIds(ids);
    }

    @RequestMapping
    public List<Routeset> getByVersion(final HttpServletResponse response,
                                       @RequestParam(required = false, defaultValue = "0") Long version) {

        final List<Long> ids = routesetRepository.findIdByVersionGreaterThan(version, new PageRequest(0, 2500));

        cacheControl.setCacheParameter(response, ids, version);
        return findByIds(ids);
    }

    public List<Routeset> findByIds(List<Long> ids) {
        return bes.transform(ids, s -> routesetRepository.findAllById(s));
    }
}
