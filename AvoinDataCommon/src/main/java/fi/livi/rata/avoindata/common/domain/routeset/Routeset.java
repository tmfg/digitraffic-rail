package fi.livi.rata.avoindata.common.domain.routeset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import edu.umd.cs.findbugs.annotations.NonNull;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Routeset {
    @Id
    public Long id;

    @JsonIgnore
    public Long version;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime messageTime;

    @Embedded
    @JsonUnwrapped
    public StringTrainId trainId;

    @Column(insertable = false,updatable = false)
    @NonNull
    @JsonIgnore
    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate virtualDepartureDate;

    public String routeType;

    public String clientSystem;

    @OneToMany(mappedBy = "routeset")
    public Set<Routesection> routesections = new HashSet<>();

    @Override
    public String toString() {
        return id.toString();
    }
}
