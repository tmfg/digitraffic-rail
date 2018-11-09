package fi.livi.rata.avoindata.common.domain.timetableperiod;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
public class TimeTablePeriod {
    @Id
    public Long id;

    public String name;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate effectiveFrom;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate effectiveTo;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate capacityAllocationConfirmDate;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate capacityRequestSubmissionDeadline;

    @OneToMany(mappedBy = "timeTablePeriod", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    public List<TimeTablePeriodChangeDate> changeDates;
}
