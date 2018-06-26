package fi.livi.rata.avoindata.LiikeInterface.purkaja.entity;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra.Aikataulukausi;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Entity
public class Aikataulujoukko {
    public static final String KEY_NAME = "ATJ_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String atjType;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime julkaisuhetki;

    @ManyToOne
    @JoinColumn(name = Aikataulukausi.KEY_NAME)
    public Aikataulukausi aikataulukausi;

    @ManyToOne
    @JoinColumn(name = Muutosajankohta.KEY_NAME)
    public Muutosajankohta muutosajankohta;

    public Long infraId;
}
