package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.*;

@Entity
@Table(name = "OPERAATTORIKOHTAINEN_JNS" )
public class OperaattorikohtainenJNS extends BaseEntity {
    public static final String KEY_NAME = "opns_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @ManyToOne
    @JoinColumn(name = Junalaji.KEY_NAME)
    public Junalaji junalaji;

    @ManyToOne
    @JoinColumn(name = Junatyyppi.KEY_NAME)
    public Junatyyppi junatyyppi;

    @ManyToOne
    @JoinColumn(name = Operaattori.KEY_NAME)
    private Operaattori operaattori;

    public Integer ylaraja;
    public Integer alaraja;
}
