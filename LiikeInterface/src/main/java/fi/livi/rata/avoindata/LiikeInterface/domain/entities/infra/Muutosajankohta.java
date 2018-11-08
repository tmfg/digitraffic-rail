package fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
public class Muutosajankohta extends BaseEntity {
    public static final String KEY_NAME = "MUA_ID";

    @Id
    @Column(name = KEY_NAME)
    private Long id;

    @Type(type = "org.hibernate.type.LocalDateType")
    @Column(name = "HAKU_LOPPU_PVM")
    public LocalDate hakuLoppupvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate voimaantuloPvm;

    @ManyToOne
    @JoinColumn(name = Aikataulukausi.KEY_NAME)
    @JsonIgnore
    public Aikataulukausi aikataulukausi;
}