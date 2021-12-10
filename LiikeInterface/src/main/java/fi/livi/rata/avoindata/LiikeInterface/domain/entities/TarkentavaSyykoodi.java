package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import java.io.Serializable;
import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

@Entity
@Table(name = "AIKTPN_TARKENTAVA_SYYKOODI")
public class TarkentavaSyykoodi implements Serializable {
    public static final String KEY_NAME = "APTSK_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String nimi;

    public String tark_syykoodi;

    public String kuvaus;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate voimassaAlkupvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate voimassaLoppupvm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Syykoodi.KEY_NAME)
    private Syykoodi syykoodi;

    public String oid;
}
