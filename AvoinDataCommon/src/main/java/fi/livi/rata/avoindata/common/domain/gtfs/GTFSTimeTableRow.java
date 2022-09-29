package fi.livi.rata.avoindata.common.domain.gtfs;

import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;
import org.joda.time.Seconds;

import javax.persistence.*;
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
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime scheduledTime;

    @Column
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime actualTime;

    @Column
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime liveEstimateTime;

    @Column
    public Boolean commercialStop;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false, insertable = false, updatable = false),
            @JoinColumn(name = "trainNumber", referencedColumnName = "trainNumber", nullable = false, insertable = false, updatable = false)})
    public GTFSTrain train;

    public int differenceInSeconds() {
        if(actualTime != null) {
            return (int) Duration.between(scheduledTime, actualTime).getSeconds();
        }
        if(liveEstimateTime != null) {
            return (int) Duration.between(scheduledTime, liveEstimateTime).getSeconds();
        }

        return 0;
    }

    public boolean hasEstimateOrActualTime() {
        return liveEstimateTime != null || actualTime != null;
    }
}
