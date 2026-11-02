package fi.livi.rata.avoindata.common.domain.common;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "last_update")
public class LastUpdate {
    @Id
    public String type;

    public Instant lastUpdated;

    public LastUpdate() {
    }

    public LastUpdate(final String type, final Instant lastUpdated) {
        this.type = type;
        this.lastUpdated = lastUpdated;
    }
}
