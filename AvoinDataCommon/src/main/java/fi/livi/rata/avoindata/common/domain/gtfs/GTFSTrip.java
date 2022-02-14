package fi.livi.rata.avoindata.common.domain.gtfs;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.LocalDate;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "gtfs_trip")
public class GTFSTrip {
    @EmbeddedId
    public GTFSTripId id;

    @NonNull
    public String tripId;

    @NonNull
    public String routeId;

    public long version;

    public GTFSTrip() {
    }

    public GTFSTrip(final @NonNull Long trainNumber,
                    final @NonNull LocalDate startDate,
                    final @NonNull LocalDate endDate,
                    final @NonNull String tripId,
                    final @NonNull String routeId,
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
