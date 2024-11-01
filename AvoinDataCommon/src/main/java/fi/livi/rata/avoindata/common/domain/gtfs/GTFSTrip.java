package fi.livi.rata.avoindata.common.domain.gtfs;

import javax.annotation.Nonnull;
import java.time.LocalDate;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "gtfs_trip")
public class GTFSTrip {
    @EmbeddedId
    public GTFSTripId id;

    @Nonnull
    public String tripId;

    @Nonnull
    public String routeId;

    public long version;

    public GTFSTrip() {
    }

    public GTFSTrip(final @Nonnull Long trainNumber,
                    final @Nonnull LocalDate startDate,
                    final @Nonnull LocalDate endDate,
                    final @Nonnull String tripId,
                    final @Nonnull String routeId,
                    final long version) {
        this.id = new GTFSTripId(trainNumber, startDate, endDate);
        this.tripId = tripId;
        this.routeId = routeId;
        this.version = version;
    }

    @Override
    public String toString() {
        return "GTFSTrip{" +
                "trainNumber=" + id.trainNumber +
                ", startDate= " + id.startDate +
                ", tripId='" + tripId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
