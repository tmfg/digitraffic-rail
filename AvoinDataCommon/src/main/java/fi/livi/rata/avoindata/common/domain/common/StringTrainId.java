package fi.livi.rata.avoindata.common.domain.common;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import java.io.Serializable;
import java.time.LocalDate;

public class StringTrainId implements Serializable {
    @NonNull
    @Column
    @ApiModelProperty(value = "Identifies the train inside a single departure date", example = "1", required = true)
    public String trainNumber;

    @Column
    @Type(type = "org.hibernate.type.LocalDateType")
    @ApiModelProperty(value = "Date of the train's first departure", required = true, example = "2017-12-01")
    public LocalDate departureDate;

    protected StringTrainId() {
    }

    public StringTrainId(@NonNull String trainNumber, LocalDate departureDate) {
        this.trainNumber = trainNumber;
        this.departureDate = departureDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof StringTrainId)) {
            return false;
        }

        StringTrainId trainId = (StringTrainId) o;

        if (!trainNumber.equals(trainId.trainNumber)) {
            return false;
        } else if (departureDate == null && trainId.departureDate == null) {
            return true;
        } else if (departureDate == null && trainId.departureDate != null) {
            return false;
        } else if (!departureDate.equals(trainId.departureDate)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public int hashCode() {
        int result = trainNumber.hashCode();
        if (departureDate != null) {
            result = 31 * result + departureDate.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", departureDate, trainNumber);
    }
}
