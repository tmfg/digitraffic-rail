package fi.livi.rata.avoindata.common.domain.gtfs;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.LocalDate;

import javax.persistence.Column;
import java.io.Serializable;
import java.util.Objects;

public class GTFSTripId implements Serializable {
    @NonNull
    @Column
    public Long trainNumber;

    @NonNull
    @Column
    public LocalDate startDate;

    public GTFSTripId() {
        // DEFAULT
    }

    public GTFSTripId(final @NonNull Long trainNumber, final @NonNull LocalDate startDate) {
        this.trainNumber = trainNumber;
        this.startDate = startDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final GTFSTripId that = (GTFSTripId) o;

        return trainNumber.equals(that.trainNumber) && startDate.equals(that.startDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNumber, startDate);
    }
}
