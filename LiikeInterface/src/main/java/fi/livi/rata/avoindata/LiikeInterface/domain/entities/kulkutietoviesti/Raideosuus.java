package fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Liikennepaikka;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "LIIKENNEPAIKAN_RAIDEOSUUS")
public class Raideosuus extends BaseEntity {
    public static final String KEY_NAME = "LPRDO_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String tunniste;

    @OneToMany(mappedBy = "raideosuus")
    public Set<RaideosuudenSijainti> raideosuudenSijaintis;

    @ManyToOne
    @JoinColumn(name = Liikennepaikka.KEY_NAME)
    public Liikennepaikka liikennepaikka;
}
