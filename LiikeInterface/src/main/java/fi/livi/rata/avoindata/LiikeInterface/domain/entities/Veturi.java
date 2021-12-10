package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@Table(name = "kp_veturi")
public class Veturi {
    public static final String KEY_NAME = "kpve_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String tunniste;

    public Integer sijainti;

    @ManyToOne
    @JoinColumn(name = Veturityyppi.KEY_NAME)
    @JsonIgnore
    public Veturityyppi veturityyppi;

    @ManyToOne
    @JoinColumn(name = Kokoonpano.KEY_NAME)
    private Kokoonpano kokoonpano;

    public String getTyyppi() {
        return veturityyppi.tyyppi;
    }

    public char getVetovoimalajilyhenne() {
        return veturityyppi.vetovoimalaji.lyhenne;
    }
}
