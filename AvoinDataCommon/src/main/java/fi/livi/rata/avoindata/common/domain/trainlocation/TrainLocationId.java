package fi.livi.rata.avoindata.common.domain.trainlocation;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
public class TrainLocationId implements Serializable {
    public TrainLocationId(final Long trainNumber, final LocalDate departureDate, final ZonedDateTime timestamp) {
        this.trainNumber = trainNumber;
        this.departureDate = departureDate;
        this.timestamp = timestamp;
    }

    public TrainLocationId() {
    }

    public Long trainNumber;

    public LocalDate departureDate;

    public ZonedDateTime timestamp;

    @Override
    public String toString() {
        return "TrainLocationId{" + "trainNumber=" + trainNumber + ", departureDate=" + departureDate + ", timestamp=" + timestamp + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final TrainLocationId that)) {
            return false;
        }
        return Objects.equals(trainNumber, that.trainNumber) && Objects.equals(departureDate, that.departureDate) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNumber, departureDate, timestamp);
    }
}
