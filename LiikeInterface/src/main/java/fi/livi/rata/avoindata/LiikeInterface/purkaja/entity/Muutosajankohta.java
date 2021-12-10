package fi.livi.rata.avoindata.LiikeInterface.purkaja.entity;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra.Aikataulukausi;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Entity
public class Muutosajankohta {
    public static final String KEY_NAME = "MUA_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime voimaantuloPvm;

    @ManyToOne
    @JoinColumn(name = Aikataulukausi.KEY_NAME)
    public Aikataulukausi aikataulukausi;
}
