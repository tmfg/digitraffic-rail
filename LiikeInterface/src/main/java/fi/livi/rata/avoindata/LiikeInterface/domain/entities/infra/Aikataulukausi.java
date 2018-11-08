package fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
public class Aikataulukausi extends BaseEntity {
    public static final String KEY_NAME = "ATKAU_ID";

    @Id
    @Column(name = KEY_NAME)
    private Long id;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaAlkuPvm;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaLoppuPvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    @Column(name = "HAKU_LOPPU_PVM")
    public LocalDate hakuLoppupvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate jakopaatosViimeistaanPvm;

    @OneToMany(mappedBy = "aikataulukausi")
    public List<Muutosajankohta> muutosajankohdat;

    public String nimi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Infra.KEY_NAME)
    @JsonIgnore
    public Infra infra;
}
