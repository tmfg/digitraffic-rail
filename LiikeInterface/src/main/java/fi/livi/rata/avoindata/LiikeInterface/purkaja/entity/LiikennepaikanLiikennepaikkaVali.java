package fi.livi.rata.avoindata.LiikeInterface.purkaja.entity;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Liikennepaikka;

import javax.persistence.*;

@Entity(name = "liikennepaikan_lpvali")
public class LiikennepaikanLiikennepaikkaVali {
    private static final String KEY_NAME = "LPLPV_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @ManyToOne
    @JoinColumn(name = Liikennepaikka.KEY_NAME)
    public Liikennepaikka liikennepaikka;

    @ManyToOne
    @JoinColumn(name = Liikennepaikkavali.KEY_NAME)
    public Liikennepaikkavali liikennepaikkavali;
}
