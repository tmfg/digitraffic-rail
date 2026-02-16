package fi.livi.rata.avoindata.server.controller.api.metadata;

import java.util.Collection;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import fi.livi.rata.avoindata.common.dao.metadata.TimeTablePeriodRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class TimeTablePeriodController extends AMetadataController {
    private final TimeTablePeriodRepository timeTablePeriodRepository;

    public TimeTablePeriodController(final TimeTablePeriodRepository timeTablePeriodRepository) {
        this.timeTablePeriodRepository = timeTablePeriodRepository;
    }

    @Operation(summary = "Returns list of time table periods")
    @RequestMapping(value = "time-table-periods", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public Collection<TimeTablePeriod> getTimeTablePeriods(final HttpServletResponse response) {
        final Collection<TimeTablePeriod> output = timeTablePeriodRepository.getTimeTablePeriods();
        setCache(response, output);
        return output;
    }
}
