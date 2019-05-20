package fi.livi.rata.avoindata.server.controller.api;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import fi.livi.rata.avoindata.server.controller.utils.FindByIdService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@XRayEnabled
@Api(tags = "routesets", description = "Returns routesets")
@RequestMapping(WebConfig.CONTEXT_PATH + "routesets")
@Transactional(timeout = 30, readOnly = true)
@RestController
public class RoutesetController extends ADataController {
    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private FindByIdService findByIdService;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CacheControl cacheControl = CacheConfig.TRAIN_RUNNING_MESSAGE_CACHECONTROL;

    private static final Comparator<Routeset> COMPARATOR = Comparator.comparing(t -> t.messageTime);

    @ApiOperation("Returns routesets for {train_number} and {departure_date}")
    @RequestMapping(value = "/{departure_date}/{train_number}", method = RequestMethod.GET)
    public List<Routeset> getRoutesetsByTrainNumber(HttpServletResponse response, @PathVariable final String train_number,
                                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        final List<Routeset> routesets = routesetRepository.findByTrainNumberAndDepartureDate(train_number, departure_date);

        cacheControl.setCacheParameter(response, routesets, -1);

        return routesets;
    }

    @ApiOperation("Returns routesets for {station} and {departure_date}")
    @RequestMapping(path = "station/{station}/{departure_date}", method = RequestMethod.GET)
    public List<Routeset> getRoutesetsByStationAndDepartureDate(HttpServletResponse response,
                                                                @PathVariable final String station,
                                                                @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        List<Long> ids = routesetRepository.findIdByStationAndDepartureDate(departure_date, station);

        cacheControl.setCacheParameter(response, ids, -1);
        return findByIds(ids);
    }

    @ApiOperation("Returns routesets that are newer than {version}")
    @RequestMapping(method = RequestMethod.GET)
    public List<Routeset> getRoutesetsByVersion(final HttpServletResponse response,
                                                @RequestParam(required = false) Long version) {
        if (version == null) {
            version = routesetRepository.getMaxVersion() - 1;
        }

        final List<Long> ids = routesetRepository.findIdByVersionGreaterThan(version, new PageRequest(0, 2500));

        cacheControl.setCacheParameter(response, ids, version);
        return findByIds(ids);
    }

    public List<Routeset> findByIds(List<Long> ids) {
        return findByIdService.findById(s -> routesetRepository.findAllById(s), ids, COMPARATOR);
    }
}
