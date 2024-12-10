package fi.livi.rata.avoindata.common.domain.composition;

import java.time.ZonedDateTime;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Schema(name = "CompositionTimeTableRow", title = "CompositionTimeTableRow", description = "Describes a point in a trains schedule where its composition changes")
public class CompositionTimeTableRow {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @JsonUnwrapped
    @Embedded
    public StationEmbeddable station;

    @Column
    public TimeTableRow.TimeTableRowType type;

    @Column
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    public ZonedDateTime scheduledTime;

    protected CompositionTimeTableRow() {
    }

    public CompositionTimeTableRow(final JourneyCompositionRow journeyCompositionRow) {
        this.station = new StationEmbeddable(journeyCompositionRow.stationShortCode, journeyCompositionRow.stationUICCode, journeyCompositionRow.countryCode);
        this.type = journeyCompositionRow.type;
        this.scheduledTime = journeyCompositionRow.scheduledTime;
    }
}
