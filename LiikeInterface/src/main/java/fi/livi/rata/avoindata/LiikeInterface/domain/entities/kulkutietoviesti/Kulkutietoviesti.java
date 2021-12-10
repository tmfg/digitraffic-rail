package fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
public class Kulkutietoviesti extends BaseEntity {
    public static final String KEY_NAME = "KTV_ID";
    @Version
    @Column(name = "ORA_ROWSCN")
    public Long version;

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String raideosuusEdellinen;
    public String raideosuusSeuraava;
    public String raideosuus;
    public String raideosuudenVarautuminen;

    public String liikennepaikka;
    public String liikennepaikkaSeuraava;
    public String liikennepaikkaEdellinen;

    public String junanumero;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime tapahtumaPvm;

    @Column(name = "LAHTO_PVM")
    @Type(type="org.hibernate.type.LocalDateType")

    public LocalDate lahtopvm;

    @Column(name = "TAPAHTUMA_V")
    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate tapahtumaV;
}
