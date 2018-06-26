package fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;

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

    @ManyToOne
    @JoinColumn(name = Infra.KEY_NAME)
    public Infra infra;
}
