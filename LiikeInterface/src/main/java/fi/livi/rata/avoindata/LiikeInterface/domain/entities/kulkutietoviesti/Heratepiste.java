package fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "HERATEPISTE")
public class Heratepiste extends BaseEntity {
    public static final String KEY_NAME = "HP_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String liikennepaikka;
    public String liikennepaikkaHerate;
    public String raideosuusHerate;
    public String varautumisenTyyppi;
    public Integer offset;
    public String tyyppi;
    public String suunnanLiikennepaikka;
}
