package fi.livi.rata.avoindata.common.domain.common;

import java.io.Serializable;
import java.time.LocalDate;
import jakarta.persistence.Column;

import javax.annotation.Nonnull;

public class TimeTableRowId implements Serializable {

    @Nonnull
    @Column
    public Long attapId;

    @Nonnull
    @Column
    public Long trainNumber;
    @Nonnull
    @Column
    public LocalDate departureDate;

    protected TimeTableRowId() {
    }

    public TimeTableRowId(final long attapId, final LocalDate departureDate, final long trainNumber) {
        this.trainNumber = trainNumber;
        this.departureDate = departureDate;
        this.attapId = attapId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final TimeTableRowId that)) return false;

        if (!attapId.equals(that.attapId)) return false;
        if (!departureDate.equals(that.departureDate)) return false;
        if (!trainNumber.equals(that.trainNumber)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = attapId.hashCode();
        result = 31 * result + trainNumber.hashCode();
        result = 31 * result + departureDate.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TimeTableRowId{" +
                "attapId=" + attapId +
                ", trainNumber=" + trainNumber +
                ", departureDate=" + departureDate +
                '}';
    }
}
