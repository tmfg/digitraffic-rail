package fi.livi.rata.avoindata.common.domain.train;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(indexes = {@Index(name = "train_trainNumber_departureDate", columnList = "trainNumber,departureDate", unique = true), @Index(name
        = "train_departureDate", columnList = "departureDate"), @Index(name = "train_version", columnList = "version")})
@Schema(name = "Train", title = "Train")
public class Train implements Comparable<Train> {
    public enum TimetableType {
        REGULAR,
        ADHOC
    }

    @EmbeddedId
    @JsonUnwrapped
    public TrainId id;

    @Embedded
    @JsonUnwrapped
    @JsonInclude(Include.NON_NULL) // Springdoc won't include embedded to OpenAPI schemas without JsonInclude
    public Operator operator;

    @Column
    @JsonIgnore
    public long trainCategoryId;

    @Column
    @JsonIgnore
    public long trainTypeId;

    @Transient
    @JsonInclude(Include.ALWAYS)
    @Schema(example = "IC")
    public String trainType;

    @Transient
    @JsonInclude(Include.ALWAYS)
    @Schema(example = "Long-distance")
    public String trainCategory;

    @Column
    @Schema(example = "Z")
    public String commuterLineID;

    @Column
    @Schema(description = "Is the train running currently or does it have actual times")
    public boolean runningCurrently;

    @Column
    @Schema(description = "Is the train wholly cancelled")
    public boolean cancelled;

    @Column
    @Schema(description = "Is the train deleted which means cancelled 10 days before its departure date")
    public Boolean deleted;

    @Column
    @Schema(description = "When was train last modified")
    public Long version;

    @Schema(description = "Is the train ADHOC or REGULAR. REGULAR trains are run for example every monday, ADHOC trains are one-time trains")
    public TimetableType timetableType;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Schema(description = "When was this train accepted to run on Finnish railways by the FTA")
    public ZonedDateTime timetableAcceptanceDate;

    @OneToMany(mappedBy = "train", fetch = FetchType.LAZY)
    public List<TimeTableRow> timeTableRows = new ArrayList<>();

    protected Train() {
    }

    public Train(final Long trainNumber, final LocalDate departureDate, final int operatorUICCode, final String operatorShortCode,
                 final long trainCategoryId, final long trainTypeId, final String commuterLineID, final boolean runningCurrently,
                 final boolean cancelled, final Long version, final TimetableType timetableType, final ZonedDateTime timetableAcceptanceDate) {
        this.id = new TrainId(trainNumber, departureDate);
        this.trainCategoryId = trainCategoryId;
        this.trainTypeId = trainTypeId;
        this.commuterLineID = commuterLineID;
        this.runningCurrently = runningCurrently;
        this.cancelled = cancelled;
        this.version = version;
        this.timetableType = timetableType;
        this.timetableAcceptanceDate = timetableAcceptanceDate;

        this.operator = new Operator();
        this.deleted = null;
        operator.operatorUICCode = operatorUICCode;
        operator.operatorShortCode = operatorShortCode;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", id.departureDate, id.trainNumber);
    }

    private static final Comparator<Train> COMPARATOR = (t1, t2) -> {
        int trainNumberCompare = t1.id.departureDate.compareTo(t2.id.departureDate);
        if (trainNumberCompare != 0) {
            return trainNumberCompare;
        }
        return t1.id.trainNumber.compareTo(t2.id.trainNumber);
    };

    @Override
    public int compareTo(Train o) {
        return COMPARATOR.compare(this, o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Train)) {
            return false;
        }

        Train train = (Train) o;

        if (!id.equals(train.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
