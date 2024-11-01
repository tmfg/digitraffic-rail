package fi.livi.rata.avoindata.server.factory;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.metadata.TimeTablePeriodRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;

@Component
public class TimeTablePeriodFactory {
    @Autowired
    private TimeTablePeriodRepository timeTablePeriodRepository;

    public TimeTablePeriod create() {
        final TimeTablePeriod timeTablePeriod = new TimeTablePeriod();

        timeTablePeriod.capacityAllocationConfirmDate = LocalDate.of(2018, 1, 1);
        timeTablePeriod.capacityRequestSubmissionDeadline = LocalDate.of(2018, 2, 2);
        timeTablePeriod.effectiveFrom = LocalDate.of(2018, 3, 3);
        timeTablePeriod.effectiveTo = LocalDate.of(2018, 4, 4);
        timeTablePeriod.id = 1L;
        timeTablePeriod.name = "Aikataulukausi 2018";

        timeTablePeriod.changeDates = new ArrayList<>();

        timeTablePeriod.changeDates.add(createTimeTablePeriodChangeDate(5, timeTablePeriod));
        timeTablePeriod.changeDates.add(createTimeTablePeriodChangeDate(6, timeTablePeriod));
        timeTablePeriod.changeDates.add(createTimeTablePeriodChangeDate(7, timeTablePeriod));
        timeTablePeriod.changeDates.add(createTimeTablePeriodChangeDate(11, timeTablePeriod));

        timeTablePeriodRepository.persist(Lists.newArrayList(timeTablePeriod));

        return timeTablePeriod;
    }

    private TimeTablePeriodChangeDate createTimeTablePeriodChangeDate(final int month, final TimeTablePeriod timeTablePeriod) {
        final TimeTablePeriodChangeDate timeTablePeriodChangeDate = new TimeTablePeriodChangeDate();
        timeTablePeriodChangeDate.capacityRequestSubmissionDeadline = LocalDate.of(2018, month, 6);
        timeTablePeriodChangeDate.effectiveFrom = LocalDate.of(2018, month, 7);
        timeTablePeriodChangeDate.id = (long) month;

        timeTablePeriodChangeDate.timeTablePeriod = timeTablePeriod;

        return timeTablePeriodChangeDate;
    }
}
