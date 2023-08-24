package fi.livi.rata.avoindata.common.domain.timetableperiod;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Type;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "TimeTablePeriodChangeDate", title = "TimeTablePeriodChangeDate")
public class TimeTablePeriodChangeDate {
    @Id
    public Long id;

    public LocalDate effectiveFrom;

    public LocalDate capacityRequestSubmissionDeadline;

    @ManyToOne
    @JsonIgnore
    public TimeTablePeriod timeTablePeriod;
}
