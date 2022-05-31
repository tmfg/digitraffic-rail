package fi.livi.rata.avoindata.common.domain.gtfs;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Entity
@Table(name = "time_table_row")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleTimeTableRow {

    @EmbeddedId
    public TimeTableRowId id;
    @Column(insertable = false, updatable = false)
    public String commercialTrack;
    @Column(insertable = false, updatable = false)
    public ZonedDateTime scheduledTime;
    @Column(insertable = false, updatable = false)
    public String stationShortCode;
    @Column(insertable = false, updatable = false)
    public TimeTableRow.TimeTableRowType type;
    @Column(insertable = false, updatable = false)
    public LocalDate departureDate;

    protected SimpleTimeTableRow() {
    }

    public SimpleTimeTableRow(final long attapId, final LocalDate departureDate, final long trainNumber, final String commercialTrack, final ZonedDateTime scheduledTime, final String stationShortCode,
                              final TimeTableRow.TimeTableRowType type) {
        id = new TimeTableRowId(attapId, departureDate, trainNumber);
        this.commercialTrack = commercialTrack;
        this.scheduledTime = scheduledTime;
        this.stationShortCode = stationShortCode;
        this.type = type;
    }

    public Long getTrainNumber() { return id.trainNumber; }

    public Long getAttapId() { return id.attapId; }

    @Override
    public String toString() {
        return "SimpleTimeTableRow{" +
                "id=" + id +
                ", commercialTrack='" + commercialTrack + '\'' +
                ", scheduledTime=" + scheduledTime +
                ", stationShortCode='" + stationShortCode + '\'' +
                ", type=" + type +
                '}';
    }
}
