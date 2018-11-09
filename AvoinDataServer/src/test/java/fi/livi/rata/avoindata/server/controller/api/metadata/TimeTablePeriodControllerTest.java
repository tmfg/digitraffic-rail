package fi.livi.rata.avoindata.server.controller.api.metadata;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.TimeTablePeriodFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class TimeTablePeriodControllerTest extends MockMvcBaseTest {
    @Autowired
    private TimeTablePeriodFactory timeTablePeriodFactory;

    @Test
    @Transactional
    public void basicGetShouldWork() throws Exception {
        timeTablePeriodFactory.create();

        ResultActions r1 = getJson("/metadata/time-table-periods");

        r1.andExpect(jsonPath("$[0].id").value("1"));
        r1.andExpect(jsonPath("$[0].name").value("Aikataulukausi 2018"));
        r1.andExpect(jsonPath("$[0].effectiveFrom").value("2018-03-03"));
        r1.andExpect(jsonPath("$[0].effectiveTo").value("2018-04-04"));
        r1.andExpect(jsonPath("$[0].capacityAllocationConfirmDate").value("2018-01-01"));
        r1.andExpect(jsonPath("$[0].capacityRequestSubmissionDeadline").value("2018-02-02"));
        r1.andExpect(jsonPath("$[0].changeDates.length()").value(4));

        r1.andExpect(jsonPath("$[0].changeDates[0].id").value(5));
        r1.andExpect(jsonPath("$[0].changeDates[0].effectiveFrom").value("2018-05-07"));
        r1.andExpect(jsonPath("$[0].changeDates[0].capacityRequestSubmissionDeadline").value("2018-05-06"));
    }
}