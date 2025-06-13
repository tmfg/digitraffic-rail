package fi.livi.rata.avoindata.common.domain.stopmonitoring;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.springframework.data.annotation.ReadOnlyProperty;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "rami_udot")
@DynamicUpdate
@DynamicInsert
public class Udot {
    @Id
    public Long id;

    @Generated
    public ZonedDateTime modifiedDb;

    public ZonedDateTime modelUpdatedTime;

    @ReadOnlyProperty
    public int trainNumber;

    @ReadOnlyProperty
    public int attapId;

    @ReadOnlyProperty
    public LocalDate trainDepartureDate;

    @ReadOnlyProperty
    public boolean unknownDelay;

    @ReadOnlyProperty
    public boolean unknownTrack;
}
