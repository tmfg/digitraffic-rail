package fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.*;

@Entity
@Table(name = "LPRAIDEOSUUDEN_SIJAINTI")
public class RaideosuudenSijainti extends BaseEntity {
    private static final String KEY_NAME = "LPRS_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String alkuRatanumero;
    @Column(name = "alku_KM")
    public String alkuKM;

    public String loppuRatanumero;
    @Column(name = "loppu_KM")
    public String loppuKM;

    @ManyToOne
    @JoinColumn(name = Raideosuus.KEY_NAME)
    private Raideosuus raideosuus;

}
