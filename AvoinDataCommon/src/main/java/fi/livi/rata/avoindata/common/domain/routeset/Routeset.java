package fi.livi.rata.avoindata.common.domain.routeset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import edu.umd.cs.findbugs.annotations.NonNull;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import io.swagger.v3.oas.annotations.media.Schema;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Schema(name = "Routeset", title = "Routeset")
public class Routeset {
    @Id
    @JsonIgnore
    public Long id;

    public Long version;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    public ZonedDateTime messageTime;

    @Embedded
    @JsonUnwrapped
    public StringTrainId trainId;

    @Column(insertable = false,updatable = false)
    @NonNull
    @JsonIgnore
    public LocalDate virtualDepartureDate;

    public String routeType;

    public String clientSystem;

    public String messageId;

    @OneToMany(mappedBy = "routeset")
    public List<Routesection> routesections = new ArrayList<>();

    @Override
    public String toString() {
        return "Routeset{" +
                "id=" + id +
                ", trainId=" + trainId +
                '}';
    }
}
