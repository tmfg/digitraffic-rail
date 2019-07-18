package fi.livi.rata.avoindata.common.domain.common;

import edu.umd.cs.findbugs.annotations.NonNull;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import java.io.Serializable;
import java.time.LocalDate;

public class TrainId implements Serializable {
    @NonNull
    @Column
    @ApiModelProperty(value = "Identifies the train inside a single departure date", example = "1", required = true)
    public Long trainNumber;
    @NonNull
    @Column
    @Type(type = "org.hibernate.type.LocalDateType")
    @ApiModelProperty(value = "Date of the train's first departure", required = true, example = "2017-12-01")
    public LocalDate departureDate;

    protected TrainId() {
    }

    public TrainId(@NonNull long trainNumber, @NonNull LocalDate departureDate) {
        this.trainNumber = trainNumber;
        this.departureDate = departureDate;
    }

    public TrainId(final JourneyComposition composition) {
        this.trainNumber = composition.trainNumber;
        this.departureDate = composition.departureDate;
    }

    public TrainId(final StringTrainId stringTrainId) {
        this.trainNumber = Long.parseLong(stringTrainId.trainNumber);
        this.departureDate = stringTrainId.departureDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrainId)) return false;

        TrainId trainId = (TrainId) o;

        if (!departureDate.equals(trainId.departureDate)) return false;
        if (!trainNumber.equals(trainId.trainNumber)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = trainNumber.hashCode();
        result = 31 * result + departureDate.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", departureDate, trainNumber);
    }
}
