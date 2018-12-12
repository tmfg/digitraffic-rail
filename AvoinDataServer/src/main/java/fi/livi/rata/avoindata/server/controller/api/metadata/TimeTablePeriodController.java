package fi.livi.rata.avoindata.server.controller.api.metadata;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import fi.livi.rata.avoindata.common.dao.metadata.TimeTablePeriodRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.Collection;

@RestController
public class TimeTablePeriodController extends AMetadataController {
    @Autowired
    private TimeTablePeriodRepository timeTablePeriodRepository;

    @ApiOperation("Returns list of time table periods")
    @RequestMapping(value = "time-table-periods", method = RequestMethod.GET)
    public Collection<TimeTablePeriod> getTimeTablePeriods(HttpServletResponse response) {
        final Collection<TimeTablePeriod> output = timeTablePeriodRepository.getTimeTablePeriods();
        setCache(response, output);
        return output;
    }
}
