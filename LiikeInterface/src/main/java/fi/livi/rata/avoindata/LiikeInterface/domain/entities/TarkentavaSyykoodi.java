package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "AIKTPN_TARKENTAVA_SYYKOODI")
public class TarkentavaSyykoodi {
    public static final String KEY_NAME = "APTSK_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String nimi;

    public String tark_syykoodi;

    public String kuvaus;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaAlkupvm;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate voimassaLoppupvm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Syykoodi.KEY_NAME)
    private Syykoodi syykoodi;
}
