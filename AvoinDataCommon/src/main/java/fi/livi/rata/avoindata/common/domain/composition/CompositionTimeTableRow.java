package fi.livi.rata.avoindata.common.domain.composition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.v3.oas.annotations.media.Schema;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

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

    public CompositionTimeTableRow(final JourneyCompositionRow journeyCompositionRow, final Composition composition) {
        this.station = new StationEmbeddable(journeyCompositionRow.stationShortCode, journeyCompositionRow.stationUICCode,
                journeyCompositionRow.countryCode);

        this.type = journeyCompositionRow.type;

        final LocalDate departureDate = composition.id.departureDate;
        final LocalDateTime scheduledTime = journeyCompositionRow.scheduledTime;

        final LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        final Duration epochDuration = Duration.between(epoch, scheduledTime);

        final LocalDateTime scheduledLocalDateTime = departureDate.atTime(0,0,0).plusNanos(epochDuration.toNanos());
        final ZonedDateTime helsinkiScheduledDatetime = scheduledLocalDateTime.atZone(ZoneId.of("Europe/Helsinki"));
        this.scheduledTime = helsinkiScheduledDatetime.withZoneSameInstant(ZoneId.of("UTC"));
    }
}
