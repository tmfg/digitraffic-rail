package fi.livi.rata.avoindata.server.controller.api;

import static fi.livi.rata.avoindata.server.controller.utils.CacheControl.addSchedulesCacheParametersForDailyResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.server.config.CacheConfig;
import fi.livi.rata.avoindata.server.config.WebConfig;
import fi.livi.rata.avoindata.server.controller.api.exception.CompositionNotFoundException;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(tags = "compositions", description = "Returns compositions of trains")
@RestController
@RequestMapping(WebConfig.CONTEXT_PATH + "compositions")
@Transactional(timeout = 30, readOnly = true)
public class CompositionController extends ADataController {
    @Autowired
    private CompositionRepository compositionRepository;


    @ApiOperation("Returns all compositions that are newer than {version}")
    @RequestMapping(method = RequestMethod.GET, path = "")
    public List<Composition> getCompositionsByVersion(@RequestParam(required = false) Long version, HttpServletResponse response) {
        if (version == null) {
            version = compositionRepository.getMaxVersion() - 1;
        }

        List<TrainId> trainIds = compositionRepository.findIdsByVersionGreaterThan(version, new PageRequest(0, 1000));
        if (!trainIds.isEmpty()) {

            List<Composition> compositions = compositionRepository.findByIds(trainIds);

            CacheConfig.COMPOSITION_CACHECONTROL.setCacheParameter(response, compositions, version);

            return compositions;
        } else {
            return new ArrayList<>();
        }
    }


    @ApiOperation("Returns all compositions for trains run on {departure_date}")
    @RequestMapping(method = RequestMethod.GET, path = "/{departure_date}")
    public Collection<Composition> getCompositionsByDepartureDate(
            @ApiParam(defaultValue = "2017-08-01") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate
                    departure_date,
            HttpServletResponse response) {


        List<Composition> list = compositionRepository.findByDepartureDateBetweenOrderByTrainNumber(departure_date);

        if (list.isEmpty()) {
            CacheControl.setCacheMaxAgeSeconds(response, 900);
            throw new CompositionNotFoundException(departure_date);
        }

        addSchedulesCacheParametersForDailyResult(departure_date, response);

        return list;
    }


    @ApiOperation("Returns composition for a specific train")
    @RequestMapping(value = "/{departure_date}/{train_number}", method = RequestMethod.GET)
    public Composition getCompositionByTrainNumberAndDepartureDate(
            @ApiParam(defaultValue = "2017-08-01") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate departure_date,
            @ApiParam(defaultValue = "1") @PathVariable("train_number") Long train_number, HttpServletResponse response) {
        List<Composition> compositions = compositionRepository.findByIds(Lists.newArrayList(new TrainId(train_number, departure_date)));

        if (compositions == null || compositions.isEmpty()) {
            throw new CompositionNotFoundException(train_number, departure_date);
        }

        CacheConfig.COMPOSITION_CACHECONTROL.setCacheParameter(response, compositions);

        return compositions.get(0);
    }
}
