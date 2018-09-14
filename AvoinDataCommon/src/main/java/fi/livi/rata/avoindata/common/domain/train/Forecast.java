package fi.livi.rata.avoindata.common.domain.train;

import java.time.ZonedDateTime;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class Forecast {
    @Id
    public Long id;

    public String source;
    public Integer difference;
    public Long version;

    @Transient
    public ZonedDateTime lastModified;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime forecastTime;

    @ManyToOne
    @JoinColumns({@JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false), @JoinColumn(name =
            "trainNumber", referencedColumnName = "trainNumber", nullable = false), @JoinColumn(name = "attapId", referencedColumnName =
            "attapId", nullable = false)})
    @JsonIgnore
    public TimeTableRow timeTableRow;

    @Override
    public String toString() {
        return "Forecast{" +
                "timeTableRow=" + timeTableRow.id +
                ", source='" + source + '\'' +
                ", forecastTime=" + forecastTime +
                ", version=" + version +
                ", id=" + id +
                ", difference=" + difference +
                '}';
    }
}
