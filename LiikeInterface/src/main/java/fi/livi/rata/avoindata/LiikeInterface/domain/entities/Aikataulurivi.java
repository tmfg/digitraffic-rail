package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import java.util.Set;

@Entity
public class Aikataulurivi {
    public static final String KEY_NAME = "AIKTR_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @ManyToOne
    @JoinColumn(name = Aikataulu.KEY_NAME)
    @JsonIgnore
    public Aikataulu aikataulu;

    @OneToMany(mappedBy = "aikataulurivi")
    @Where(clause = "attap_type='LAHTO'")
    public Set<Aikataulutapahtuma> lahto;

    @OneToMany(mappedBy = "aikataulurivi")
    @Where(clause = "attap_type='SAAP'")
    public Set<Aikataulutapahtuma> saapuminen;

    @ManyToOne
    @JoinColumn(name = LiikennepaikanRaide.KEY_NAME)
    @JsonView({AikatauluController.class})
    public LiikennepaikanRaide liikennepaikanRaide;

    @ManyToOne
    @JoinColumn(name = Liikennepaikka.KEY_NAME)
    @OneToMany(fetch = FetchType.EAGER)
    @JsonView({AikatauluController.class})
    public Liikennepaikka liikennepaikka;
}
