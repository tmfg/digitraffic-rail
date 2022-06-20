package fi.livi.rata.avoindata.common.domain.trainreadymessage;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import edu.umd.cs.findbugs.annotations.NonNull;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(indexes = {@Index(name = "tr20_departureDate_trainNumber", columnList = "departureDate,trainNumber"), @Index(name = "tr20_version", columnList = "version")})
@Schema(description = "TrainRunMessages are generated when a train either enters or exists a TrackSection")
public class TrainRunningMessage {
    @Id
    public Long id;
    public Long version;

    @Embedded
    @JsonUnwrapped
    public StringTrainId trainId;

    @Column(insertable = false,updatable = false)
    @NonNull
    @JsonIgnore
    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate virtualDepartureDate;

    @Column
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    @Schema(description = "Timestamp when the message was generated")
    public ZonedDateTime timestamp;

    public String trackSection;
    public String nextTrackSection;
    public String previousTrackSection;

    public String station;
    public String nextStation;
    public String previousStation;

    @Schema(description = "OCCUPY = train entered TrackSection, RELEASE=train exited TrackSection")
    public TrainRunningMessageTypeEnum type;

    @Override
    public String toString() {
        return "TrainRunningMessage{" +
                "id=" + id +
                '}';
    }


}
