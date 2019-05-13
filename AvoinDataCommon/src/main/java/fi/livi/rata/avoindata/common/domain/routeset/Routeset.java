package fi.livi.rata.avoindata.common.domain.routeset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import edu.umd.cs.findbugs.annotations.NonNull;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Routeset {
    @Id
    @JsonIgnore
    public Long id;

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
