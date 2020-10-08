package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunatapahtumaPrimaryKey;

@Entity
@Table(name = "JUPA_TAPAHTUMA")
public class JupaTapahtuma extends BaseEntity {

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime suunniteltuAika;

    @OneToMany(mappedBy = "jupaTapahtuma")
    public Set<Syytieto> syytietos;

    @EmbeddedId
    public JunatapahtumaPrimaryKey id;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @Column(name = "minimiennuste_aika")
    public ZonedDateTime automaattiennusteAika;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime kasiennusteAika;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime toteutunutAika;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @JsonIgnore
    public ZonedDateTime muokkausAika;

    @Column(name = "ATTAP_TYPE")
    public String tyyppi;

    public String jupaTila;

    public Boolean kaupallinen;

    @Column(name = "ORA_ROWSCN")
    public Long version;

    public String lviTila;
    public String lviLahde;
    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime lviTilanMuokkausaika;

    @ManyToOne
    @JoinColumn(name = Liikennepaikka.KEY_NAME)
    public Liikennepaikka liikennepaikka;

    @ManyToOne
    @JoinColumn(name = LiikennepaikanRaide.KEY_NAME)
    @JsonUnwrapped
    public LiikennepaikanRaide liikennepaikanRaide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Aikataulutapahtuma.KEY_NAME, insertable = false, updatable = false)
    public Aikataulutapahtuma aikataulutapahtuma;

    @ManyToOne
    @JoinColumns(value = {
            @JoinColumn(name = "lahtopvm", referencedColumnName = "lahtopvm", insertable = false, updatable = false),
            @JoinColumn(name = "junanumero", referencedColumnName = "junanumero", insertable = false, updatable = false)})
    private Junapaiva junapaiva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Kokoonpano.KEY_NAME)
    private Kokoonpano kokoonpano;

    @Override
    public String toString() {
        return "JupaTapahtuma{" +
                "id=" + id +
                '}';
    }
}
