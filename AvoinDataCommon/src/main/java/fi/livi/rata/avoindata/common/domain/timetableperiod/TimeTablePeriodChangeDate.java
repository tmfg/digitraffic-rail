package fi.livi.rata.avoindata.common.domain.timetableperiod;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Type;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.time.LocalDate;

@Entity
public class TimeTablePeriodChangeDate {
    @Id
    public Long id;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate effectiveFrom;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate capacityRequestSubmissionDeadline;

    @ManyToOne
    @JsonIgnore
    public TimeTablePeriod timeTablePeriod;
}
