package fi.livi.rata.avoindata.common.domain.stopsector;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.train.Train;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "stop_sector_queue_item")
public class StopSectorQueueItem {
    @EmbeddedId
    public TrainId id;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @CreatedDate
    public ZonedDateTime created;

    public String source;

    protected StopSectorQueueItem() {
    }

    public StopSectorQueueItem(final TrainId id, final String source) {
        this.id = id;
        this.source = source;
        this.created = ZonedDateTime.now();
    }

    public StopSectorQueueItem(final Train train) {
        this(new TrainId(train.id.trainNumber, train.id.departureDate), "Train");
    }

    public StopSectorQueueItem(final Composition composition) {
        this(new TrainId(composition.id.trainNumber, composition.id.departureDate), "Composition");
    }
}
