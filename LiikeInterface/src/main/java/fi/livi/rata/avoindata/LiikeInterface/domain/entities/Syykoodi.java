package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.metadata.SyyluokkaController;

@Entity
@Table(name = "AIKTPN_SYYKOODI")
public class Syykoodi implements Serializable {
    public static final String KEY_NAME = "APSK_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Syyluokka.KEY_NAME)
    @JsonView(JunapaivaController.class)
    public Syyluokka syyluokka;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate voimassaAlkupvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate voimassaLoppupvm;

    public String nimi;

    public String syykoodi;

    public String oid;

    @OneToMany(mappedBy = "syykoodi")
    @JsonView(SyyluokkaController.class)
    public List<TarkentavaSyykoodi> tarkentavaSyykoodiList;
}
