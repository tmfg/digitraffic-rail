package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.Where;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;

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
    @JsonView(AikatauluController.class)
    public Set<Aikataulutapahtuma> lahto;

    @OneToMany(mappedBy = "aikataulurivi")
    @Where(clause = "attap_type='SAAP'")
    @JsonView(AikatauluController.class)
    public Set<Aikataulutapahtuma> saapuminen;

    @OneToMany(mappedBy = "aikataulurivi", fetch = FetchType.LAZY)
    @JsonView(JunapaivaController.class)
    public Set<Raidemuutos> raidemuutos;

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
