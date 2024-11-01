package fi.livi.rata.avoindata.common.domain.gtfs;

import javax.annotation.Nonnull;
import java.time.LocalDate;

import jakarta.persistence.Column;
import java.io.Serializable;
import java.util.Objects;

public class GTFSTripId implements Serializable {
    @Nonnull
    @Column
    public Long trainNumber;

    @Nonnull
    @Column
    public LocalDate startDate;

    @Nonnull
    @Column
    public LocalDate endDate;

    public GTFSTripId() {
        // DEFAULT
    }

    public GTFSTripId(final @Nonnull Long trainNumber, final @Nonnull LocalDate startDate, final @Nonnull LocalDate endDate) {
        this.trainNumber = trainNumber;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final GTFSTripId that = (GTFSTripId) o;
        return trainNumber.equals(that.trainNumber) && startDate.equals(that.startDate) && endDate.equals(that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trainNumber, startDate, endDate);
    }
}
