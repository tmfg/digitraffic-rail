package fi.livi.rata.avoindata.common.domain.gtfs;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.LocalDate;

import javax.persistence.Column;
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

    public GTFSTrip() {
    }

    public GTFSTrip(final @NonNull Long trainNumber,
                    final @NonNull LocalDate startDate,
                    final LocalDate endDate,
                    final @NonNull String tripId,
                    final @NonNull String routeId) {
        this.id = new GTFSTripId(trainNumber, startDate, endDate);
        this.tripId = tripId;
        this.routeId = routeId;
    }

    @Override
    public String toString() {
        return "GTFSTrip{" +
                "trainNumber=" + id.trainNumber +
                ", startDate= " + id.startDate +
                ", tripId='" + tripId + '\'' +
                ", routeId='" + routeId + '\'' +
                '}';
    }
}
