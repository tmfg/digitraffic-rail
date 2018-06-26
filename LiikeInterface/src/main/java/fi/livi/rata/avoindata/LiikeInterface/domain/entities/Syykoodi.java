package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.metadata.SyyluokkaController;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "AIKTPN_SYYKOODI")
public class Syykoodi {
    public static final String KEY_NAME = "APSK_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Syyluokka.KEY_NAME)
    private Syyluokka syyluokka;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaAlkupvm;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaLoppupvm;

    public String nimi;

    public String syykoodi;

    @OneToMany(mappedBy = "syykoodi")
    @JsonView(SyyluokkaController.class)
    public List<TarkentavaSyykoodi> tarkentavaSyykoodiList;
}
