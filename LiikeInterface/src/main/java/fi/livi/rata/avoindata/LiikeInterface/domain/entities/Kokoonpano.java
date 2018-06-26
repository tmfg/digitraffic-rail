package fi.livi.rata.avoindata.LiikeInterface.domain.entities;


import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Set;

@Entity
public class Kokoonpano extends BaseEntity {
    public static final String KEY_NAME="KOPA_ID";

    @Version
    @Column(name = "ORA_ROWSCN")
    public Long version;

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate lahtoPvm;

    public Integer kokonaispituus;

    public Integer jarrupainonopeus;

    @ManyToOne
    @JoinColumn(name = Aikataulutapahtuma.KEY_NAME)
    public Aikataulutapahtuma aikataulutapahtuma;

    @ManyToOne
    @JoinColumn(name = "saap_attap_id")
    public Aikataulutapahtuma viimeinenAikataulutapahtuma;

    @ManyToOne
    @JoinColumn(name = Aikataulu.KEY_NAME)
    public Aikataulu aikataulu;

    @OneToMany(mappedBy = "kokoonpano")
    public Set<Vaunu> vaunus;

    @OneToMany(mappedBy = "kokoonpano")
    public Set<Veturi> veturis;

    @Override
    public String toString() {
        return String.format("%s - %s (%s)",lahtoPvm,aikataulu.aikataulunJunanumero.junanumero,id);
    }
}
