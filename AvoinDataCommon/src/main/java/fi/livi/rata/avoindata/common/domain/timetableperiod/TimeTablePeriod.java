package fi.livi.rata.avoindata.common.domain.timetableperiod;

import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "TimeTablePeriod", title = "TimeTablePeriod")
public class TimeTablePeriod {
    @Id
    public Long id;

    public String name;

    public LocalDate effectiveFrom;

    public LocalDate effectiveTo;

    public LocalDate capacityAllocationConfirmDate;

    public LocalDate capacityRequestSubmissionDeadline;

    @OneToMany(mappedBy = "timeTablePeriod", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    public List<TimeTablePeriodChangeDate> changeDates;
}
