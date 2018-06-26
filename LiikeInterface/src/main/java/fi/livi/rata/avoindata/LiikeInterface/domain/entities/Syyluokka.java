package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.metadata.SyyluokkaController;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "AIKTPN_SYYLUOKKA")
public class Syyluokka {
    public static final String KEY_NAME = "APSL_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String nimi;

    public String tunnus;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaAlkupvm;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaLoppupvm;

    @OneToMany(mappedBy = "syyluokka")
    @JsonView(SyyluokkaController.class)
    public Set<Syykoodi> syykoodis;

    public boolean salliSyykoodiAvoindatassa;
}
