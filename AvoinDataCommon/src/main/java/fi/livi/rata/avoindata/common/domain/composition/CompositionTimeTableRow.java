package fi.livi.rata.avoindata.common.domain.composition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.v3.oas.annotations.media.Schema;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.*;

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

    public CompositionTimeTableRow(JourneyCompositionRow journeyCompositionRow, Composition composition) {
        this.station = new StationEmbeddable(journeyCompositionRow.stationShortCode, journeyCompositionRow.stationUICCode,
                journeyCompositionRow.countryCode);

        this.type = journeyCompositionRow.type;

        final LocalDate departureDate = composition.id.departureDate;
        final LocalDateTime scheduledTime = journeyCompositionRow.scheduledTime;

        LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        Duration epochDuration = Duration.between(epoch, scheduledTime);

        LocalDateTime scheduledLocalDateTime = departureDate.atTime(0,0,0).plusNanos(epochDuration.toNanos());
        ZonedDateTime helsinkiScheduledDatetime = scheduledLocalDateTime.atZone(ZoneId.of("Europe/Helsinki"));
        this.scheduledTime = helsinkiScheduledDatetime.withZoneSameInstant(ZoneId.of("UTC"));
    }
}
