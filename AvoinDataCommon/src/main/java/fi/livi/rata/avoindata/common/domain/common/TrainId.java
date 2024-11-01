package fi.livi.rata.avoindata.common.domain.common;

import static java.util.Comparator.comparing;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Comparator;

import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;

import javax.annotation.Nonnull;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public class TrainId implements Serializable, Comparable {
    @Nonnull
    @Column
    @Schema(description = "Identifies the train inside a single departure date", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    public Long trainNumber;
    @Nonnull
    @Column
    @Schema(description = "Date of the train's first departure", requiredMode = Schema.RequiredMode.REQUIRED, example = "2017-12-01")
    public LocalDate departureDate;

    protected TrainId() {
    }

    public TrainId(@Nonnull final long trainNumber, @Nonnull final LocalDate departureDate) {
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final TrainId trainId)) return false;

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

    @Override
    public int compareTo(final Object o) {
        if (!(o instanceof final TrainId another)) {
            throw new IllegalStateException("Can only compare against another TrainId");
        }

        final Comparator<TrainId> comparing = comparing(s -> s.departureDate);
        return comparing.thenComparing(s -> s.trainNumber).compare(this, another);
    }
}
