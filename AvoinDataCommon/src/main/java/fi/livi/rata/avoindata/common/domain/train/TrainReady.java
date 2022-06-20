package fi.livi.rata.avoindata.common.domain.train;

import java.time.ZonedDateTime;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(description = "Operator has to ask permission (=TrainReady) to leave certain stations")
public class TrainReady {
    public enum TrainReadySource {
        PHONE,
        LIIKE, UNKNOWN, KUPLA
    }

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @ManyToOne
    @JoinColumns({@JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false), @JoinColumn(name =
            "trainNumber", referencedColumnName = "trainNumber", nullable = false), @JoinColumn(name = "attapId", referencedColumnName =
            "attapId", nullable = false)})
    @JsonIgnore
    public TimeTableRow timeTableRow;

    @Schema(description = "How was the permission given")
    public TrainReadySource source;

    @Type(type = "org.hibernate.type.NumericBooleanType")
    @Schema(description = "Was the permission given")
    public boolean accepted;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @Schema(description = "When was the permission given")
    public ZonedDateTime timestamp;

//    @OneToOne(mappedBy = "trainReady")
//    @JsonIgnore
//    public TimeTableRow timeTableRow;
}
