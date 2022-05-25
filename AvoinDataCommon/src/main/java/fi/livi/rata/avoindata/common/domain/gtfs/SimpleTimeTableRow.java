package fi.livi.rata.avoindata.common.domain.gtfs;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Entity
@Table(name = "time_table_row")
public class SimpleTimeTableRow {

    @EmbeddedId
    public TimeTableRowId id;
    @Column
    public String commercialTrack;
    @Column
    public ZonedDateTime scheduledTime;
    @Column
    public String stationShortCode;
    @Column
    public TimeTableRow.TimeTableRowType type;

    protected SimpleTimeTableRow() {
    }

    public Long getTrainNumber() { return id.trainNumber; }

}
