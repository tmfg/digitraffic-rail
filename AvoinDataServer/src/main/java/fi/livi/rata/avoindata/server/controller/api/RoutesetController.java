package fi.livi.rata.avoindata.server.controller.api;

import java.time.LocalDate;
import java.util.Comparator;
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

import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "routesets", description = "Returns routesets")
@RequestMapping(WebConfig.CONTEXT_PATH + "routesets")
@RestController
public class RoutesetController extends ADataController {
    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private BatchExecutionService bes;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CacheControl cacheControl = CacheConfig.TRAIN_RUNNING_MESSAGE_CACHECONTROL;

    private static final Comparator<Routeset> COMPARATOR = Comparator.comparing(t -> t.messageTime);

    @Operation(summary = "Returns routesets for {train_number} and {departure_date}")
    @RequestMapping(value = "/{departure_date}/{train_number}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<Routeset> getRoutesetsByTrainNumber(
            HttpServletResponse response,
            @Parameter(description = "train_number") @PathVariable final String train_number,
            @Parameter(description = "departure_date") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        final List<Routeset> routesets = routesetRepository.findByTrainNumberAndDepartureDate(train_number, departure_date);

        cacheControl.setCacheParameter(response, routesets, -1);

        return routesets;
    }

    @Operation(summary = "Returns routesets for {station} and {departure_date}")
    @RequestMapping(path = "station/{station}/{departure_date}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<Routeset> getRoutesetsByStationAndDepartureDate(
            HttpServletResponse response,
            @Parameter(description = "station") @PathVariable final String station,
            @Parameter(description = "departure_date") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure_date) {

        List<Long> ids = routesetRepository.findIdByStationAndDepartureDate(departure_date, station);

        cacheControl.setCacheParameter(response, ids, -1);
        return findByIds(ids);
    }

    @Operation(summary = "Returns routesets that are newer than {version}")
    @RequestMapping(method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public List<Routeset> getRoutesetsByVersion(final HttpServletResponse response,
                                                @RequestParam(required = false) Long version) {
        if (version == null) {
            version = routesetRepository.getMaxVersion() - 1;
        }

        final List<Long> ids = routesetRepository.findIdByVersionGreaterThan(version, PageRequest.of(0, 2500));

        cacheControl.setCacheParameter(response, ids, version);
        return findByIds(ids);
    }

    public List<Routeset> findByIds(List<Long> ids) {
        return bes.mapAndSort(s -> routesetRepository.findAllById(s), ids, COMPARATOR);
    }
}
