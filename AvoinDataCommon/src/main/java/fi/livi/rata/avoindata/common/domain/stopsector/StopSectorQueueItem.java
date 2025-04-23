package fi.livi.rata.avoindata.common.domain.stopsector;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.train.Train;
import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "stop_sector_queue_item")
public class StopSectorQueueItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Nonnull
    public Long trainNumber;

    @Nonnull
    public LocalDate departureDate;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @CreatedDate
    public ZonedDateTime created;

    public String source;

    protected StopSectorQueueItem() {
    }

    public StopSectorQueueItem(final TrainId id, final String source) {
        this.trainNumber = id.trainNumber;
        this.departureDate = id.departureDate;
        this.source = source;
        this.created = ZonedDateTime.now();
    }

    public StopSectorQueueItem(final Train train) {
        this(train.id, "Train");
    }

    public StopSectorQueueItem(final Composition composition) {
        this(composition.id, "Composition");
    }
}
