package fi.livi.rata.avoindata.LiikeInterface.domain.entities.routeset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import javax.persistence.Version;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

@Entity
public class Routeset extends BaseEntity {
    public static final String KEY_NAME = "ROSE_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @Version
    @Column(name = "ORA_ROWSCN")
    public Long version;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate departureDate;

    @Type(type="org.hibernate.type.LocalDateTimeType")
    @Column(name = "message_time")
    @JsonIgnore
    public LocalDateTime messageTimeAsLocal;

    @Transient
    public ZonedDateTime messageTime;

    public String routeType;
    public String clientSystem;
    public String trainNumber;
    @Column(name = "MESSAGE_ID")
    public String messageId;

    @OneToMany(mappedBy = "routeset")
    public List<Routesection> routesections;
}
