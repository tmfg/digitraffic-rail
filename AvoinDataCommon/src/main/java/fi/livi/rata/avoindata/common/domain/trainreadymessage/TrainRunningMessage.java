package fi.livi.rata.avoindata.common.domain.trainreadymessage;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import io.swagger.v3.oas.annotations.media.Schema;

import javax.annotation.Nonnull;

@Entity
@Table(indexes = {@Index(name = "tr20_departureDate_trainNumber", columnList = "departureDate,trainNumber"), @Index(name = "tr20_version", columnList = "version")})
@Schema(name = "TrainRunningMessage", title = "TrainRunningMessage", description = "TrainRunMessages are generated when a train either enters or exists a TrackSection")
public class TrainRunningMessage {
    @Id
    public Long id;
    public Long version;

    @Embedded
    @JsonUnwrapped
    public StringTrainId trainId;

    @Column(insertable = false,updatable = false)
    @Nonnull
    @JsonIgnore
    public LocalDate virtualDepartureDate;

    @Column
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Schema(description = "Timestamp when the message was generated")
    public ZonedDateTime timestamp;

    public String trackSection;
    public String nextTrackSection;
    public String previousTrackSection;

    public String station;
    public String nextStation;
    public String previousStation;

    @Schema(description = "OCCUPY = train entered TrackSection, RELEASE = train exited TrackSection")
    public TrainRunningMessageTypeEnum type;

    @Override
    public String toString() {
        return "TrainRunningMessage{" +
                "id=" + id +
                '}';
    }


}
