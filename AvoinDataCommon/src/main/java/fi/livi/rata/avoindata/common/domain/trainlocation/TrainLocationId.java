package fi.livi.rata.avoindata.common.domain.trainlocation;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;


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
}
