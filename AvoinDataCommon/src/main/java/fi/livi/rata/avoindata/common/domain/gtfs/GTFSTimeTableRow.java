package fi.livi.rata.avoindata.common.domain.gtfs;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "time_table_row")
public class GTFSTimeTableRow {
    public enum TimeTableRowType {
        ARRIVAL,
        DEPARTURE
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
