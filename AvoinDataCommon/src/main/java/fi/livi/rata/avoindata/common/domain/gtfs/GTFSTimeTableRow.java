package fi.livi.rata.avoindata.common.domain.gtfs;

import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.ZonedDateTime;

@Entity
@Immutable
@Table(name = "time_table_row")
public class GTFSTimeTableRow {
    public enum TimeTableRowType {
        ARRIVAL,
        DEPARTURE
    }
    public enum EstimateSourceEnum {
        LIIKE_USER,
        MIKU_USER,
        LIIKE_AUTOMATIC,
        UNKNOWN,
        COMBOCALC
    }

    @EmbeddedId
    public TimeTableRowId id;

    @Column
    public String stationShortCode;

    @Column(nullable = false)
    public TimeTableRow.TimeTableRowType type;

    @Column(nullable = false)
    public boolean cancelled;

    @Column
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    public ZonedDateTime scheduledTime;

    @Column
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    public ZonedDateTime actualTime;

    @Column
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    public ZonedDateTime liveEstimateTime;

    @Column
    public Boolean commercialStop;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "trainNumber", referencedColumnName = "trainNumber", nullable = false, insertable = false, updatable = false)})
    public GTFSTrain train;

    public int delayInSeconds() {
        return (int) Duration.between(scheduledTime, getActualOrEstimate()).getSeconds();
    }

    public ZonedDateTime getActualOrEstimate() {
        if(actualTime != null) {
            return actualTime;
        } else if(liveEstimateTime != null) {
            return liveEstimateTime;
        }

        return scheduledTime;
    }

    public boolean hasEstimateOrActualTime() {
        return liveEstimateTime != null || actualTime != null;
    }
}
