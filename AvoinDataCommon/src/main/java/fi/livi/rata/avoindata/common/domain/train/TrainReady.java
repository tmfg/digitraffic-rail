package fi.livi.rata.avoindata.common.domain.train;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Entity
@ApiModel(description = "Operator has to ask permission (=TrainReady) to leave certain stations")
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

    @ApiModelProperty("How was the permission given")
    public TrainReadySource source;

    @Type(type = "org.hibernate.type.NumericBooleanType")
    @ApiModelProperty("Was the permission given")
    public boolean accepted;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @ApiModelProperty("When was the permission given")
    public ZonedDateTime timestamp;

//    @OneToOne(mappedBy = "trainReady")
//    @JsonIgnore
//    public TimeTableRow timeTableRow;
}
