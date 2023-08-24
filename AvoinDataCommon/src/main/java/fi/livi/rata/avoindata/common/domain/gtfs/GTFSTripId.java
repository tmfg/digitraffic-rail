package fi.livi.rata.avoindata.common.domain.gtfs;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.LocalDate;

import jakarta.persistence.Column;
import java.io.Serializable;
import java.util.Objects;

public class GTFSTripId implements Serializable {
    @NonNull
    @Column
    public Long trainNumber;

    @NonNull
    @Column
    public LocalDate startDate;

    @NonNull
    @Column
    public LocalDate endDate;

    public GTFSTripId() {
        // DEFAULT
    }

    public GTFSTripId(final @NonNull Long trainNumber, final @NonNull LocalDate startDate, final @NonNull LocalDate endDate) {
        this.trainNumber = trainNumber;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GTFSTripId that = (GTFSTripId) o;
        return trainNumber.equals(that.trainNumber) && startDate.equals(that.startDate) && endDate.equals(that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNumber, startDate, endDate);
    }
}
