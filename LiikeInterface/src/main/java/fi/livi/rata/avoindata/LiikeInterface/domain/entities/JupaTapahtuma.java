package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunatapahtumaPrimaryKey;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.Set;

@Entity
@Table(name = "JUPA_TAPAHTUMA")
public class JupaTapahtuma extends BaseEntity {

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime suunniteltuAika;

    @OneToMany(mappedBy = "jupaTapahtuma")
    public Set<Syytieto> syytietos;

    @EmbeddedId
    public JunatapahtumaPrimaryKey id;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    @Column(name = "minimiennuste_aika")
    public ZonedDateTime automaattiennusteAika;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime kasiennusteAika;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime toteutunutAika;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @JsonIgnore
    public ZonedDateTime muokkausAika;

    @Column(name="ATTAP_TYPE")
    public String tyyppi;

    public String jupaTila;

    public Boolean kaupallinen;

    @Column(name = "ORA_ROWSCN")
    public Long version;

    public String lviTila;
    public String lviLahde;
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime lviTilanMuokkausaika;

    @ManyToOne
    @JoinColumn(name = Liikennepaikka.KEY_NAME)
    public Liikennepaikka liikennepaikka;

    @ManyToOne
    @JoinColumn(name = LiikennepaikanRaide.KEY_NAME)
    @JsonUnwrapped
    public LiikennepaikanRaide liikennepaikanRaide;

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
