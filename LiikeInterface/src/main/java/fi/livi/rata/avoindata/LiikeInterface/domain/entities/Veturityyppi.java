package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.localization.Vetovoimalaji;

import javax.persistence.*;

@Entity
public class Veturityyppi {
    public static final String KEY_NAME = "vtyyp_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String tyyppi;

    @ManyToOne
    @JoinColumn(name = Vetovoimalaji.KEY_NAME)
    public Vetovoimalaji vetovoimalaji;
}
