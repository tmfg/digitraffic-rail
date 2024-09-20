package fi.livi.rata.avoindata.common.domain.train;

import java.time.ZonedDateTime;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;

@Entity
@Schema(name = "TrainReady",
        title = "TrainReady",
        description = "Operator has to ask permission (=TrainReady) to leave certain stations")
public class TrainReady {
    public enum TrainReadySource {
        PHONE,
        LIIKE, UNKNOWN, KUPLA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @ManyToOne
    @JoinColumns({ @JoinColumn(name = "departureDate",
                               referencedColumnName = "departureDate",
                               nullable = false), @JoinColumn(name =
                                                                      "trainNumber",
                                                              referencedColumnName = "trainNumber",
                                                              nullable = false), @JoinColumn(name = "attapId",
                                                                                             referencedColumnName =
                                                                                                     "attapId",
                                                                                             nullable = false) })
    @JsonIgnore
    public TimeTableRow timeTableRow;

    @Enumerated(EnumType.STRING)
    @Schema(description = "How was the permission given")
    public TrainReadySource source;

    @Convert(converter = org.hibernate.type.NumericBooleanConverter.class)
    @Schema(description = "Was the permission given")
    public boolean accepted;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Schema(description = "When was the permission given")
    public ZonedDateTime timestamp;

    //    @OneToOne(mappedBy = "trainReady")
    //    @JsonIgnore
    //    public TimeTableRow timeTableRow;
}
