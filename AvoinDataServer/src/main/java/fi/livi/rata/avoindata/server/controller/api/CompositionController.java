package fi.livi.rata.avoindata.server.controller.api;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.addSchedulesCacheParametersForDailyResult;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.composition.FindCompositionsByVersionService;
import fi.livi.rata.avoindata.common.dao.train.FindByTrainIdService;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.exception.CompositionNotFoundException;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "compositions", description = "Returns compositions of trains")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "compositions")
public class CompositionController extends ADataController {
    public static final int MAX_ANNOUNCED_COPOSITIONS = 1000;
    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private FindByTrainIdService findByTrainIdService;

    @Autowired
    private FindCompositionsByVersionService compositionService;

    @Operation(summary = "Returns all compositions that are newer than {version}")
    @RequestMapping(method = RequestMethod.GET, path = "")
    @Transactional(timeout = 30, readOnly = true)
    public List<Composition> getCompositionsByVersion(@Parameter(description = "version") @RequestParam(required = false)
                                                      final Long version, final HttpServletResponse response) {

        final List<Composition> compositions = compositionService.findByVersionGreaterThan(version, MAX_ANNOUNCED_COPOSITIONS);
        if (!compositions.isEmpty()) {
            CacheConfig.COMPOSITION_CACHECONTROL.setCacheParameter(response, compositions, version);
        }
        return compositions;
    }

    @Operation(summary = "Returns all compositions for trains run on {departure_date}")
    @RequestMapping(method = RequestMethod.GET, path = "/{departure_date}")
    @Transactional(timeout = 30, readOnly = true)
    public Collection<Composition> getCompositionsByDepartureDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate departure_date,
            final HttpServletResponse response) {


        final List<Composition> list = compositionRepository.findByDepartureDateBetweenOrderByTrainNumber(departure_date);

        if (list.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, 900);
            throw new CompositionNotFoundException(departure_date);
        }

        addSchedulesCacheParametersForDailyResult(departure_date, response);

        return list;
    }

    @Operation(summary = "Returns composition for a specific train")
    @RequestMapping(value = "/{departure_date}/{train_number}", method = RequestMethod.GET)
    @Transactional(timeout = 30, readOnly = true)
    public Composition getCompositionByTrainNumberAndDepartureDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            final LocalDate departure_date,
            @PathVariable("train_number")
            final Long train_number, final HttpServletResponse response) {
        final List<Composition> compositions = findByTrainIdService.findCompositions(Lists.newArrayList(new TrainId(train_number, departure_date)));

        if (compositions == null || compositions.isEmpty()) {
            throw new CompositionNotFoundException(train_number, departure_date);
        }

        CacheConfig.COMPOSITION_CACHECONTROL.setCacheParameter(response, compositions);

        return compositions.getFirst();
    }
}
