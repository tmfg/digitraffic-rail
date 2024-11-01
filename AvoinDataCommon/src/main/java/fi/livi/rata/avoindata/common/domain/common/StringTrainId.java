package fi.livi.rata.avoindata.common.domain.common;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.annotation.Nonnull;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public class StringTrainId implements Serializable {
    @Nonnull
    @Column
    @Schema(description = "Identifies the train inside a single departure date", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    public String trainNumber;

    @Column
    @Schema(description = "Date of the train's first departure", requiredMode = Schema.RequiredMode.REQUIRED, example = "2017-12-01")
    public LocalDate departureDate;

    protected StringTrainId() {
    }

    public StringTrainId(@Nonnull final String trainNumber, final LocalDate departureDate) {
        this.trainNumber = trainNumber;
        this.departureDate = departureDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof StringTrainId)) {
            return false;
        }

        final StringTrainId trainId = (StringTrainId) o;

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
