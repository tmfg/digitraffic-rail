package fi.livi.rata.avoindata.updater.service.gtfs;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class GTFSTrainBuilder {
    private final TrainId trainId;

    private final List<GTFSTimeTableRow> rows = new ArrayList<>();
    private boolean cancelled = false;
    private long version = 1;

    public GTFSTrainBuilder(final TrainId trainId) {
        this.trainId = trainId;
    }

    public GTFSTrainBuilder cancelled() {
        this.cancelled = true;

        return this;
    }

    public GTFSTrainBuilder version(final long version) {
        this.version = version;

        return this;
    }

    public GTFSTrainBuilder departure(final String stationCode, final long offsetMinutes) {
        return this.addRow(TimeTableRow.TimeTableRowType.DEPARTURE, stationCode, offsetMinutes);
    }

    public GTFSTrainBuilder arrival(final String stationCode, final long offsetMinutes) {
        return this.addRow(TimeTableRow.TimeTableRowType.ARRIVAL, stationCode, offsetMinutes);
    }

    public GTFSTrainBuilder rowCancelled() {
        if(this.rows.isEmpty()) throw new IllegalArgumentException("now rows");

        this.rows.get(this.rows.size() - 1).cancelled = true;

        return this;
    }

    /// set the estimate, offset from the scheduled time
    public GTFSTrainBuilder rowEstimate(final long offsetMinutes) {
        if(this.rows.isEmpty()) throw new IllegalArgumentException("now rows");

        this.rows.get(this.rows.size() - 1).actualTime = this.rows.get(this.rows.size() - 1).scheduledTime.plusMinutes(offsetMinutes);

        return this;
    }

    private GTFSTrainBuilder addRow(final TimeTableRow.TimeTableRowType type, final String stationCode, final long offsetMinutes) {
        if(this.rows.isEmpty()) {
            if(type == TimeTableRow.TimeTableRowType.ARRIVAL) throw new IllegalArgumentException("first row must be departure!");
        } else if(this.rows.get(this.rows.size() - 1).type == type) throw new IllegalArgumentException("fix row types!");

        final GTFSTimeTableRow row = new GTFSTimeTableRow();
        row.type = type;
        row.stationShortCode = stationCode;
        row.commercialStop = true;
        row.scheduledTime = this.trainId.departureDate.atStartOfDay(ZoneId.of("UTC")).plusMinutes(offsetMinutes);

        this.rows.add(row);

        return this;
    }

    public GTFSTrain build() {
        final GTFSTrain train = new GTFSTrain();

        train.id = this.trainId;
        train.cancelled = this.cancelled;
        train.version = this.version;
        train.timeTableRows = this.rows;

        return train;
    }
}
